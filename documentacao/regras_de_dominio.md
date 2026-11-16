# Regras de Domínio

Regras de negócio por agregado, **como estão implementadas hoje** (código em
`src/main/java/io/github/gabrielivo/oficina`). Substitui o antigo `instructions.md`.
O modelo físico (tabelas, colunas e relacionamentos) fica em `documentacao/modelo_de_dados.md`.

Convenções gerais:
- IDs são `String` com `UUID.randomUUID().toString()`, gerados no construtor ou no `@PrePersist`.
- Invariantes simples (campo obrigatório, valor positivo) ficam no construtor ou no método da
  entidade e lançam `IllegalArgumentException` (→ HTTP 400). Regras que precisam consultar o
  repositório (unicidade, existência) ficam no `*Service` e lançam a exceção de domínio do
  módulo, `<Modulo>Exception` (→ HTTP 422).
- As entidades não expõem setters; o estado muda por métodos com nome de negócio
  (`atualizar`, `inativar`, `avancarStatus`, `reduzirEstoque`...).

## Cliente (`domain/cliente`)
- CPF e nome são obrigatórios (`Cliente`). O CPF tem 11 dígitos e é validado por `@CpfValido` no request.
- O CPF é único (`ClienteService.validarCpfDisponivel` → `ClienteException`).
- Status `ATIVO` (padrão na criação) ou `INATIVO`, alterado via `inativar()`/`reativar()`.
- `Endereco` é opcional e é persistido em cascata com o cliente.
- **Contrato com a Lambda:** `ClienteService.consultarStatusPorCpf` responde `{existe, ativo}`
  em `GET /internal/clientes/{cpf}/status`. Ver `documentacao/rfcs/0003-estrategia-de-autenticacao.md`.

## Veículo (`domain/veiculo`)
- Placa, marca e modelo são obrigatórios.
- A placa é única (`VeiculoService` → `VeiculoException`).
- Pertence a um cliente existente.
- `atualizar` altera só marca, modelo e ano; a placa e o cliente são imutáveis.

## Ordem de Serviço (`domain/ordemServico`)
- É aberta com cliente e veículo existentes (`OrdemServicoService.abrir`) e já pode receber itens.
- Status inicial: `RECEBIDA`. Máquina de estados em `StatusOrdemServico.podeTransicionarPara`
  (seção 3.1 do `plano_fase4.md`):

  ```
  RECEBIDA → EM_DIAGNOSTICO → AGUARDANDO_APROVACAO → AGUARDANDO_PAGAMENTO → EM_EXECUCAO → FINALIZADA → ENTREGUE
  (qualquer status antes de FINALIZADA) → CANCELADA
  ```
  `ENTREGUE` e `CANCELADA` são finais. "Ativos" = todos menos `FINALIZADA`, `ENTREGUE` e `CANCELADA`.
- **Toda** mudança de status passa por `OrdemServico.alterarStatus(novo, origem, motivo)`: valida a transição
  (inválida → `OrdemServicoException`, sem efeito), atualiza `statusDesde` (base da métrica
  `oficina.ordens_servico.tempo_no_status`) e grava uma linha no **histórico** (`HistoricoStatusOrdemServico`):
  status anterior, novo, data/hora, origem (`USUARIO` | `SAGA` | `SISTEMA`) e motivo (≤ 255 caracteres).
  A abertura grava a primeira linha (anterior nulo → `RECEBIDA`). Consulta: `GET /ordens-servico/{id}/historico`.
- `avancarStatus()` vai só para o próximo status (origem `USUARIO`). `atualizarStatusExterno(s)` aceita apenas
  uma transição válida (origem `SISTEMA`).
- Resposta de orçamento: só quando o status é `AGUARDANDO_APROVACAO`. Se aprovado, vai para
  `AGUARDANDO_PAGAMENTO` (o pagamento é cobrado antes da execução; até a Saga, o avanço para `EM_EXECUCAO`
  é manual). Se recusado, permanece em `AGUARDANDO_APROVACAO` (o `statusDesde` é renovado, sem linha de
  histórico; a compensação que cancela a OS vem na Fase 5).
- Cada transição também registra o evento de domínio `StatusOrdemServicoAlterado` (a abertura não). O service o
  grava no **outbox** na mesma transação; depois do commit ele é publicado em `oficina.eventos`
  (`os.status-alterado`). A notificação ao cliente (`StatusNotificationPort`, e-mail ou no-op) é feita por quem
  consome esse evento no próprio OS Service, com inbox (sem e-mail repetido em duplicata), retry e DLQ (ADR 0010).
  Uma mudança desfeita (rollback) nunca notifica.
- Itens não podem ser **adicionados** com a OS fora de um status ativo (`FINALIZADA`, `ENTREGUE`, `CANCELADA`).
- `valorTotal` = soma de `valor × quantidade` dos itens, recalculada a cada alteração.
- Cancelamento (`OrdemServico.cancelar` / `POST /ordens-servico/{id}/cancelar`, motivo opcional): permitido em
  qualquer status ativo; leva a OS para `CANCELADA` e registra o motivo (padrão "Cancelamento solicitado").
  Em `FINALIZADA`, `ENTREGUE` ou `CANCELADA` → `OrdemServicoException`. Enquanto o estoque vive neste serviço,
  o service também repõe o estoque das peças e remove os itens. A compensação entre serviços vem na Fase 5.
- Listagem (`GET /ordens-servico?status=...&pagina=0&tamanho=20`): filtro de status e paginação feitos no
  banco (tamanho 1–100). Sem filtro, só as ativas. Ordem fixa por prioridade (`EM_EXECUCAO`,
  `AGUARDANDO_PAGAMENTO`, `AGUARDANDO_APROVACAO`, `EM_DIAGNOSTICO`, `RECEBIDA`, demais) e depois por `criadoEm`.

## Mensageria (`domain/mensageria`, `application/mensageria`)
- **Outbox** (`MensagemOutbox`): mensagem gravada na transação do chamador (`OutboxService.registrar` exige
  transação ativa) com o envelope JSON completo (`messageId`, `type`, `sagaId` = id da OS, `osId`, `occurredAt`,
  `payload`). Só é marcada como enviada depois do publisher confirm; mensagem não roteada, recusada ou sem
  confirmação continua pendente, com `tentativas` e `ultimoErro`. O publicador para no primeiro erro do ciclo
  para não inverter a ordem.
- **Inbox** (`MensagemProcessada`): o `messageId` é registrado na mesma transação do manipulador; duplicata é
  ignorada. Tipo sem manipulador → DLQ, sem registro no inbox. Falha do manipulador → rollback e nova tentativa
  (3 no total, com backoff) → DLQ.

## Item da Ordem de Serviço (`ItemOrdemServico`)
- Tipo `SERVICO`: quantidade fixa em 1 e nenhuma referência a peça.
- Tipo `PECA`: exige `pecaId` de uma peça existente e quantidade > 0 (validado no service). Ao
  adicionar, o estoque da peça é decrementado; ao remover o item ou cancelar a OS, é reposto.

## Peça (`domain/peca`)
- Nome obrigatório; preço > 0; estoque ≥ 0; estoque mínimo ≥ 0.
- `reduzirEstoque(q)`: exige q > 0 e estoque suficiente (senão `IllegalStateException`).
- `reporEstoque(q)`: exige q > 0.
- Estoque **baixo**: `quantidadeEstoque ≤ estoqueMinimo`. **Crítico**: `quantidadeEstoque ≤ estoqueMinimo / 2`.

## Pagamento (`domain/pagamento`)
- Referencia uma OS existente; valor > 0; forma obrigatória (`PIX`, `DINHEIRO`, `CARTAO`).
- No máximo um pagamento por OS (`PagamentoService` → `PagamentoException`).

## Usuário (`domain/usuario`) e autenticação
- `Usuario` é o login de staff (login + senha com hash BCrypt). O login é único (`UsuarioService`).
- Para trocar a senha, a senha atual precisa estar correta.
- Há dois tipos de principal no JWT, distinguidos pela claim `tipo`: ausente ou `USUARIO` = staff
  (`UserDetailsServiceImpl`); `CLIENTE` = token emitido pela Lambda (`ClienteUserDetailsService`).

## Regras previstas e ainda não implementadas
Constavam no `instructions.md`, mas **não** existem no código. Ficam aqui como backlog e não devem ser
tratadas como comportamento atual:
- "OS não pode ser finalizada sem itens": `avancarStatus` não verifica itens.
- "Valor do item ≥ 0": `ItemOrdemServico` não valida o valor.
- Remoção de item de OS `FINALIZADA`/`ENTREGUE`/`CANCELADA`: `removerItem` não verifica o status (só a adição
  verifica). Resolvido na Fase 3 (itens passam ao Execution Service).
- "Data de fechamento" ao finalizar: não existe campo próprio; usa-se `statusDesde`.
