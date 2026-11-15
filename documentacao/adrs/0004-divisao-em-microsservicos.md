# ADR 0004: Divisão em três microsserviços

- Status: Aceita
- Data: 2026-11-15

## Contexto

A Fase 4 do desafio exige pelo menos três microsserviços, cada um com
repositório, infraestrutura e banco de dados próprios, comunicando-se por
REST e mensageria, sem acesso ao banco de outro serviço. Hoje toda a regra de
negócio vive na aplicação `oficina` (monólito modular: cliente, veículo,
ordem de serviço, peça/estoque, pagamento, usuário/auth).

## Decisão

Dividir o domínio em três serviços:

| Serviço | Repositório | Banco | Dono de | Não é dono de |
|---|---|---|---|---|
| **OS Service** | `oficina` (evoluído) | PostgreSQL | Cliente, Veículo, Usuário/Auth (emissão do JWT de staff), Ordem de Serviço, histórico de status, orquestrador da Saga | Peças/estoque, orçamento, pagamento |
| **Execution Service** | `oficina-execution-service` (novo) | PostgreSQL (instância própria) | Catálogo de peças e estoque (com reserva), fila de execução (diagnóstico e reparo), progresso | Preço final, pagamento, status oficial da OS |
| **Billing Service** | `oficina-billing-service` (novo) | MongoDB | Orçamento (snapshot dos itens), aprovação/recusa, cobrança e pagamento (Mercado Pago), estorno | Estoque, execução |

Regras que acompanham a divisão:

- **Nenhum serviço acessa o banco de outro.** Comandos e eventos trafegam via
  RabbitMQ (ADR 0006); REST síncrono só para consultas estritamente
  necessárias.
- **Sem biblioteca compartilhada** entre repositórios: cada serviço define os
  próprios DTOs de mensagem; o contrato oficial é o catálogo
  `documentacao/contratos_mensagens.md` (neste repositório), validado por
  testes de contrato em cada consumidor.
- Os três serviços validam o mesmo JWT HS256 (segredo `JWT_SECRET`); só o OS
  Service emite tokens de staff. A claim `tipo` e o endpoint
  `/internal/clientes/{cpf}/status` (consumidos pela `oficina-auth-lambda`)
  não mudam de contrato.

**Por que o estoque vai para o Execution Service:** é ele que diagnostica e
consome as peças. Com o estoque lá, reservar (ao concluir o diagnóstico),
consumir (ao iniciar a execução) e liberar (na compensação) viram passos
reais da Saga entre serviços, e o OS Service fica focado em OS e
orquestração.

## Alternativas consideradas

- **Estoque no OS Service**: mantém o código atual, mas a reserva vira
  operação local do orquestrador; a Saga perde a compensação entre serviços
  mais natural do domínio e o OS Service continua acumulando
  responsabilidades.
- **Serviço de Estoque separado (4 serviços)**: fronteira mais pura, porém
  aumenta o custo de infraestrutura, pipelines e coordenação sem ganho para o
  escopo do desafio; o estoque só é consumido pela execução.
- **Orçamento no OS Service, só pagamento no Billing**: o orçamento é o
  documento que o pagamento cobra (valor, itens, aprovação); separá-los
  obrigaria o Billing a consultar o OS Service para cada cobrança.

## Consequências

- Peças, `ItemPeca`, `PecaService`/`PecaController` e a lógica de estoque
  saem do OS Service (Etapa 3.6); `Pagamento` sai na Fase 5. Até lá o fluxo
  antigo de abertura de OS com itens fica incompleto (aceito; a branch
  continua verde).
- Os itens da OS no OS Service passam a ser um snapshot somente leitura,
  recebido em `DiagnosticoConcluido`.
- Consistência entre serviços passa a ser eventual, coordenada pela Saga
  (ADR 0005) com outbox/inbox (ADR 0009).
- Cada repositório novo nasce com o mesmo ferramental do `oficina`
  (`CLAUDE.md`, ArchUnit, Spotless, JaCoCo, Flyway ou índices versionados,
  Swagger).
- Complementa o ADR 0003 (split em quatro repositórios de infraestrutura e
  Lambda), que permanece válido.
