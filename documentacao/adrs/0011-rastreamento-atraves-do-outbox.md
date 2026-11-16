# ADR 0011: Trace contínuo através do outbox e correlation id = traceId

- Status: Aceita
- Data: 2026-11-15

## Contexto

O plano da Fase 4 fixa OpenTelemetry → OTel Collector → Jaeger e exige que
"uma requisição HTTP que publica uma mensagem apareça no Jaeger como um único
trace". Com o Transactional Outbox (ADR 0009), a mensagem não é publicada na
thread da requisição: um job agendado a envia segundos depois. A instrumentação
automática do `RabbitTemplate` cria o span de envio como filho do contexto
**corrente** da thread do job, ou seja, num trace novo e desconectado da
requisição. O serviço também já tinha um `X-Correlation-Id` próprio (UUID) nos
logs, que viraria um segundo identificador paralelo ao `traceId`.

## Decisão

1. Instrumentação pelo starter oficial do Spring Boot 4,
   `spring-boot-starter-opentelemetry` (Micrometer Tracing + bridge OTel +
   exportador OTLP/HTTP para o collector). Observação ligada no `RabbitTemplate`
   e no listener (propaga o `traceparent` W3C nos headers AMQP).
2. O outbox guarda o `traceparent` de quem gravou a mensagem (coluna
   `trace_parent`). O publicador abre um span `outbox publicar <tipo>` filho
   desse contexto e só então chama o `RabbitTemplate`; envio e consumo ficam no
   trace da requisição original.
3. As execuções do agendador do outbox (a cada 1 s) e as requisições ao
   `/actuator` não geram trace (`management.observations.enable.tasks.scheduled=false`
   e um `ObservationPredicate`), para não inundar o Jaeger.
4. O correlation id passa a ser o próprio `traceId` quando o chamador não envia
   `X-Correlation-Id`; se enviar, o valor é mantido e gravado no span como
   `correlation.id`. Os logs trazem `traceId`, `spanId` e `correlationId` (MDC).

## Alternativas consideradas

- **Span links** em vez de pai/filho no publicador: semanticamente mais preciso
  para processamento em lote, mas o Jaeger mostraria dois traces e o critério
  de aceite pede um só.
- **Publicar na thread da requisição após o commit** (`AFTER_COMMIT`): mantém o
  trace sem guardar contexto, mas reabre o risco de perder a mensagem que o
  outbox resolve.
- **Manter o UUID próprio como correlation id**: dois ids sem relação para a
  mesma requisição; buscar no Jaeger a partir de um log exigiria o `traceId` de
  qualquer forma.

## Consequências

- Um único trace atravessa HTTP → outbox → RabbitMQ → consumidor; os serviços
  das Fases 3–5 devem continuar o trace a partir do header `traceparent`.
- A duração do trace inclui a espera no outbox (até o intervalo do publicador).
- No Spring Boot 4 a propagação só é configurada com exportação de tracing
  habilitada; nos testes usa-se `@AutoConfigureTracing` e desliga-se apenas o
  exportador OTLP (`management.tracing.export.otlp.enabled=false`).
- Métricas continuam no Prometheus (`management.otlp.metrics.export.enabled=false`).
