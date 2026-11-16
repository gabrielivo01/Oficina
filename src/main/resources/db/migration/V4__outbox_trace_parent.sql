-- Etapa 2.5: contexto de trace (W3C traceparent) de quem gravou a mensagem, para a publicação continuar o mesmo trace.
ALTER TABLE outbox ADD COLUMN trace_parent VARCHAR(100);
