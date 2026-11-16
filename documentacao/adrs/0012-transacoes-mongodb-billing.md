# ADR 0012: Transações MongoDB e outbox no Billing Service

- Status: Aceita
- Data: 2026-11-16

## Contexto

O Billing Service persiste orçamento/cobrança e publica eventos da Saga. Uma mudança de estado sem o
evento (ou um evento publicado antes de um rollback) deixa os serviços inconsistentes. O inbox também
precisa ser confirmado na mesma unidade atômica que o comando processado. O Billing usa MongoDB,
enquanto a configuração local da plataforma já fornece o replica set `rs0`.

## Decisão

Usar transações MongoDB com `MongoTransactionManager`. Cada processamento de comando grava seu resultado,
o documento da inbox e os eventos do outbox na mesma transação. Respostas HTTP também gravam estado e
outbox juntos. Os índices são inicializados de forma idempotente por um `ApplicationRunner`, com versão
registrada em `schema_migrations`.

O MongoDB de desenvolvimento e o StatefulSet do Billing executam como replica set `rs0` de um membro.
O outbox usa publisher confirms e só marca a mensagem como enviada após confirmação e roteamento. A inbox
usa `messageId` como `_id` único.

Efeitos externos de e-mail não são executados durante a transação: o pedido de envio é gravado em uma
coleção de outbox na transação do orçamento, e um publicador separado tenta o SMTP depois do commit.

## Alternativas consideradas

- **Evento embutido no documento de negócio:** evitaria transações entre coleções, mas acoplaria os
  formatos de evento aos documentos e complicaria inbox, múltiplos eventos e reenvio.
- **MongoDB standalone:** não oferece transações multi-documento; exigiria outro desenho de atomicidade.
- **`@TransactionalEventListener(AFTER_COMMIT)` sem outbox para mensagens/e-mails:** uma queda após o
  commit e antes da publicação perderia o evento ou a notificação.

## Consequências

- Dev, teste de integração e deploy precisam de MongoDB em replica set. Esta implementação não inicia
  containers nem depende deles nos testes unitários.
- A entrega RabbitMQ e SMTP é pelo menos uma vez; consumidores são idempotentes e um e-mail pode ser
  repetido se o processo cair depois do envio SMTP e antes de marcar o documento como enviado.
- Índices são versionados, idempotentes e criados no startup; a alteração incompatível de um índice
  existente exige uma nova versão/migração explícita.
