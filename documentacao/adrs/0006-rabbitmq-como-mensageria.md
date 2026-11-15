# ADR 0006: RabbitMQ como mensageria

- Status: Aceita
- Data: 2026-11-15
- Substitui parcialmente: ADR 0001

## Contexto

O ADR 0001 decidiu que toda a comunicação entre componentes seria REST
síncrono e já previa revisão "se surgir um fluxo que não seja
request/response por natureza". A Saga da Fase 4 (ADR 0005) é exatamente
esse caso: passos longos (diagnóstico, aprovação do cliente, pagamento),
que dependem de pessoas e de um gateway externo, entre três serviços que
precisam continuar funcionando quando outro estiver fora do ar. O desafio
também exige mensageria assíncrona.

## Decisão

- **Comandos e eventos da Saga trafegam via RabbitMQ** (Spring AMQP).
- **REST síncrono continua** para consultas e para o fluxo de autenticação
  por CPF (Lambda → `/internal/clientes/{cpf}/status`), que segue o ADR 0001.
- Topologia:
  - Exchange `oficina.comandos` (direct), uma fila de comandos por serviço:
    `execution.comandos`, `billing.comandos`.
  - Exchange `oficina.eventos` (topic); o OS Service consome em
    `os.saga.eventos`.
  - Cada fila com DLQ (`*.dlq`) e retry com backoff (3 tentativas) antes da
    DLQ.
  - Mensagens persistentes, filas duráveis, publisher confirms ligados.
- Topologia versionada (`definitions.json` no `oficina-infra-k8s`, carregado
  no boot), igual no Docker Compose e no kind.
- O contexto de trace (W3C `traceparent`) vai nos headers AMQP, propagado
  pelo Micrometer/OpenTelemetry.
- Envelope, outbox e idempotência: ADR 0009.

## Alternativas consideradas

- **Kafka**: log durável e replay são ótimos para streaming e event
  sourcing, mas o caso aqui é troca de comandos/eventos com roteamento,
  DLQ e retry por fila — nativos no RabbitMQ e que no Kafka exigem
  convenções adicionais. Kafka também é mais pesado para rodar localmente e
  no kind do CI.
- **AWS SQS/SNS ou EventBridge**: gerenciados e coerentes com a Fase 3, mas
  a Fase 4 é 100% local e de custo zero (ADR 0008); emuladores (LocalStack)
  adicionam outra peça frágil.
- **Manter só REST, com retries**: acopla a disponibilidade dos serviços
  entre si e obriga o orquestrador a fazer polling de passos que dependem de
  pessoas (aprovação, pagamento).

## Consequências

- ADR 0001 passa a "Parcialmente substituído por 0006": continua valendo
  para consultas e para a autenticação por CPF.
- RabbitMQ entra na plataforma local (Compose e kind) e passa a ser
  dependência de execução dos três serviços; testes de integração usam
  Testcontainers RabbitMQ.
- Mensagens que esgotam o retry vão para a DLQ: precisam de métrica e
  alerta (Fase 8) e de um procedimento de reprocessamento manual.
- Entrega "pelo menos uma vez": exige consumidores idempotentes (ADR 0009).
