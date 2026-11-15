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
- Status inicial: `RECEBIDA`. Fluxo linear, definido em `StatusOrdemServico.podeTransicionarPara`:

  ```
  RECEBIDA → EM_DIAGNOSTICO → AGUARDANDO_APROVACAO → EM_EXECUCAO → FINALIZADA → ENTREGUE
  ```
- `avancarStatus()` vai só para o próximo status. `atualizarStatusExterno(s)` aceita apenas uma
  transição válida. Transição inválida → `OrdemServicoException`.
- Resposta de orçamento: só quando o status é `AGUARDANDO_APROVACAO`. Se aprovado, vai para `EM_EXECUCAO`;
  se recusado, permanece em `AGUARDANDO_APROVACAO` (o `statusDesde` é renovado).
- Cada mudança de status atualiza `statusDesde` (base da métrica `oficina.ordens_servico.tempo_no_status`)
  e dispara `StatusNotificationPort.enviarAtualizacao`, quando há um adapter configurado.
- Itens não podem ser **adicionados** com a OS em `FINALIZADA` ou `ENTREGUE`.
- `valorTotal` = soma de `valor × quantidade` dos itens, recalculada a cada alteração.
- Cancelamento (`OrdemServicoService.cancelar`): proibido em `FINALIZADA`/`ENTREGUE`. Repõe o estoque das
  peças e remove todos os itens. Não existe status `CANCELADA`, então o status atual é mantido.
- A listagem geral exclui `FINALIZADA`/`ENTREGUE` e ordena por prioridade
  (`EM_EXECUCAO`, `AGUARDANDO_APROVACAO`, `EM_DIAGNOSTICO`, `RECEBIDA`) e depois por `criadoEm`.

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
- Remoção de item de OS `FINALIZADA`/`ENTREGUE`: `removerItem` não verifica o status (só a adição verifica).
- "Data de fechamento" ao finalizar: não existe campo próprio; usa-se `statusDesde`.
