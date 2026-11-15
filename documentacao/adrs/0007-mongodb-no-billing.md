# ADR 0007: MongoDB no Billing Service

- Status: Aceita
- Data: 2026-11-15

## Contexto

O desafio exige ao menos um banco SQL e um NoSQL. OS Service e Execution
Service têm dados fortemente relacionais e transacionais (cliente ↔
veículo ↔ OS; estoque com reserva e `@Version`) e ficam em PostgreSQL
(ADR 0004). O Billing Service guarda orçamentos e cobranças.

## Decisão

Usar **MongoDB** (Spring Data MongoDB) no Billing Service.

- `Orcamento` é um **documento**: `osId`, itens como **snapshot imutável**
  (descrição, tipo, quantidade, preço unitário no momento do diagnóstico),
  valor total, status (`PENDENTE` → `APROVADO` | `RECUSADO` | `CANCELADO`),
  datas e quem respondeu.
- `Cobranca` é outro documento: `osId`, `orcamentoId`, valor, `idExterno`
  do Mercado Pago, status (`PENDENTE` → `APROVADA` | `REJEITADA` |
  `CANCELADA` | `ESTORNADA`), link de pagamento.
- Outbox e inbox como coleções (inbox com índice único em `messageId`).
- Índices criados de forma versionada.
- A forma de garantir atomicidade entre a mudança de estado e o outbox
  (transação Mongo com replica set de 1 nó **ou** evento gravado dentro do
  próprio documento) será decidida e justificada num ADR do repositório
  `oficina-billing-service` (Etapa 4.1).

## Alternativas consideradas

- **PostgreSQL também no Billing**: atenderia tecnicamente, mas o desafio
  exige um NoSQL, e o orçamento seria modelado como cabeçalho + itens
  normalizados, quando na prática é um documento fechado que nunca é
  consultado item a item.
- **MongoDB no Execution Service**: o estoque precisa de reserva atômica com
  controle de concorrência sobre várias peças ao mesmo tempo — melhor
  atendido por transação relacional e lock otimista.
- **DynamoDB**: gerenciado na AWS, fora do escopo local e de custo zero
  (ADR 0008).

## Consequências

- O snapshot dos itens no orçamento torna o valor cobrado independente de
  mudanças posteriores de preço no catálogo do Execution.
- Ferramental diferente dos outros serviços: sem Flyway/JPA; testes de
  integração com Testcontainers MongoDB; replica set pode ser exigido em dev
  e em teste se a opção for transação Mongo.
- MongoDB próprio (StatefulSet) nos manifests do Billing; mais um banco para
  operar e observar.
