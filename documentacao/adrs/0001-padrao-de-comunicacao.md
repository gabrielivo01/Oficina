# ADR 0001: Padrão de comunicação — REST síncrono

- Status: Aceita
- Data: 2026-09-14

## Contexto

Com a adição do Lambda de autenticação e do API Gateway, o sistema passou a
ter mais de um componente se comunicando entre si (Lambda → aplicação
principal via `/internal/clientes/{cpf}/status`; API Gateway → Lambda; API
Gateway → aplicação). Era preciso decidir se essa comunicação seria síncrona
(REST/HTTP) ou baseada em mensageria/eventos (SQS, SNS, EventBridge).

## Decisão

Toda comunicação entre componentes continua **síncrona via HTTP/REST**.

## Consequências

- **A favor**: o fluxo de autenticação por CPF é inerentemente
  request/response (o cliente espera um token na mesma chamada) — mensageria
  assíncrona não se encaixa nesse caso de uso sem adicionar polling ou
  WebSocket para o cliente. A latência adicional de uma function serverless
  chamando a aplicação via HTTP é aceitável (a function já paga o custo de
  cold start; um HTTP call a mais não muda a natureza síncrona esperada pelo
  usuário).
- **Contra / trade-off aceito**: se a aplicação principal estiver
  indisponível, a autenticação por CPF falha imediatamente (sem fila de
  retry) — hoje isso resulta em `500` do Lambda. Aceitável para o estágio
  atual do projeto; um endurecimento futuro poderia adicionar retry com
  backoff no `HttpClienteStatusPort` antes de considerar mensageria.
- **Quando revisar esta decisão**: se surgir um fluxo que não seja
  request/response por natureza (ex.: notificação assíncrona de mudança de
  status de OS para múltiplos consumidores, hoje resolvido por
  `StatusNotificationPort`/e-mail direto), vale reconsiderar SNS/EventBridge
  para esse fluxo específico — não é necessário migrar tudo de uma vez.
