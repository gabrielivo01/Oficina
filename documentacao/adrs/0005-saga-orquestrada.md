# ADR 0005: Saga orquestrada

- Status: Aceita
- Data: 2026-11-15

## Contexto

O ciclo de vida de uma ordem de serviço passa a atravessar três serviços
(ADR 0004): diagnóstico e reserva de peças (Execution), orçamento e
pagamento (Billing) e o status oficial da OS (OS Service). Não há transação
distribuída; cada passo é local ao seu serviço. Falhas no meio do caminho
(orçamento recusado, pagamento rejeitado ou não confirmado no prazo,
execução recusada) precisam desfazer os passos anteriores (liberar reserva,
cancelar cobrança, estornar pagamento). O desafio exige Saga com
compensação, documentada e justificada.

## Decisão

Usar **Saga orquestrada**, com o orquestrador **dentro do OS Service** e o
estado persistido no banco dele (tabela `saga_ordem_servico`: `sagaId` = id
da OS, `etapaAtual`, `situacao` (`EM_ANDAMENTO` | `COMPENSANDO` |
`CONCLUIDA` | `COMPENSADA` | `FALHA_COMPENSACAO`), `prazoExpiracao`,
`tentativas`, `@Version`, timestamps).

- O orquestrador é uma **máquina de estados pura no domínio**
  (`SagaOrdemServico.aoReceber(evento) → List<Comando>`), testável sem
  Spring. A camada application só carrega/salva a saga, atualiza o status da
  OS e o histórico (origem `SAGA`) e grava os comandos no outbox, tudo na
  mesma transação.
- Fluxo feliz: `SolicitarDiagnostico` → `DiagnosticoConcluido` (estoque
  reservado) → `GerarOrcamento` → `OrcamentoAprovado` → `CriarCobranca` →
  `PagamentoConfirmado` → `IniciarExecucao` → `ExecucaoFinalizada`
  (saga `CONCLUIDA`, OS `FINALIZADA`). A tabela completa está na seção 3.2 do
  plano da Fase 4 e será reproduzida no README.
- Compensações:
  - orçamento recusado → `LiberarReservaPecas` → OS `CANCELADA`;
  - pagamento rejeitado ou prazo expirado → `CancelarCobranca` (se pendente)
    → `LiberarReservaPecas` → OS `CANCELADA`;
  - execução recusada → `EstornarPagamento` → `LiberarReservaPecas` → OS
    `CANCELADA`;
  - cancelamento manual da OS dispara a compensação da etapa atual.
- **Timeouts:** job agendado procura sagas com `prazoExpiracao` vencido
  (ex.: pagamento não confirmado em 30 min, configurável) e dispara a
  compensação.
- **Compensações idempotentes e reexecutáveis.** Após N tentativas sem
  sucesso, a saga vai para `FALHA_COMPENSACAO`, com log de erro, métrica e
  alerta Prometheus, e fica visível em `GET /ordens-servico/{id}/saga`.
- Eventos fora de ordem ou duplicados são ignorados com segurança (a máquina
  de estados só aceita o evento esperado para a etapa atual; duplicatas já
  são barradas pelo inbox, ADR 0009).

## Alternativas consideradas

- **Saga coreografada** (cada serviço reage aos eventos dos outros, sem
  coordenador): menos acoplamento a um ponto central, mas o fluxo e as
  compensações ficam espalhados por três repositórios; entender "em que pé
  está a OS X" exige correlacionar eventos de todos os serviços, e testar as
  compensações ponta a ponta fica mais difícil. Com três compensações
  diferentes e timeout de pagamento, o custo de raciocinar sobre a
  coreografia supera o benefício.
- **Orquestrador como serviço separado**: isola a coordenação, mas cria um
  quarto serviço e um banco a mais; o status oficial da OS teria de ser
  sincronizado com outro serviço. O OS Service já é o dono do status, então
  é o lugar natural do orquestrador.
- **Framework de workflow (Temporal, Camunda, Axon)**: resolve
  persistência, retries e timeouts, mas adiciona infraestrutura pesada e
  esconde a lógica que o desafio pede para demonstrar e documentar.
- **2PC/XA**: não suportado entre PostgreSQL, MongoDB e RabbitMQ de forma
  prática, e bloqueia recursos entre serviços.

## Consequências

- Fluxo e compensações ficam explícitos num único lugar, fáceis de testar
  (unitários exaustivos da máquina de estados, integração com participantes
  simulados, BDD com Cucumber) e de rastrear (uma consulta mostra etapa e
  situação; traces no Jaeger).
- O OS Service vira ponto central de coordenação: se cair, as sagas param
  (mas não se perdem — o estado está no banco e as mensagens ficam nas
  filas). Aceito.
- Novos status da OS: `AGUARDANDO_PAGAMENTO` e `CANCELADA`; toda transição
  grava `historico_status_os`.
- Os participantes (Execution, Billing) não conhecem o fluxo: apenas
  executam comandos e publicam eventos.
