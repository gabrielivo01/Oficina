# ADR 0009: Transactional Outbox e consumidores idempotentes

- Status: Aceita
- Data: 2026-11-15

## Contexto

Com a Saga sobre RabbitMQ (ADRs 0005 e 0006), cada passo precisa mudar o
estado local **e** publicar uma mensagem. Publicar direto de dentro de um
`@Transactional` cria dois modos de falha: a mensagem sai e a transação faz
rollback (evento fantasma), ou a transação faz commit e a publicação falha
(evento perdido, saga travada). Do lado do consumo, o RabbitMQ entrega
"pelo menos uma vez": redeliveries, retries e reenvios do outbox geram
duplicatas. O mesmo problema já existe hoje em escala menor: a notificação
de mudança de status da OS é enviada antes do commit.

## Decisão

1. **Transactional Outbox em todo serviço que publica.** A mudança de estado
   e a mensagem são gravadas na **mesma transação**, numa tabela/coleção
   `outbox` (id, tipo, exchange, routingKey, payload JSON, criadoEm,
   enviadoEm, tentativas). Um publicador agendado lê os pendentes, publica
   com publisher confirms e marca como enviado **só após o ack**. Nunca
   publicar direto de dentro de `@Transactional`.
2. **Inbox nos consumidores.** Toda mensagem tem `messageId`; o consumidor
   registra o id numa tabela/coleção `inbox` (chave única) na mesma
   transação do processamento e ignora duplicatas.
3. **Envelope padrão** (JSON), igual em todos os serviços:
   ```json
   {
     "messageId": "uuid",
     "type": "OrcamentoAprovado",
     "sagaId": "uuid (= id da OS)",
     "osId": "uuid",
     "occurredAt": "ISO-8601",
     "payload": { }
   }
   ```
   O contexto de trace (W3C `traceparent`) vai nos headers AMQP.
4. **Contratos sem biblioteca compartilhada**: cada serviço define seus DTOs;
   o catálogo oficial fica em `documentacao/contratos_mensagens.md` e cada
   consumidor tem teste de contrato que desserializa o JSON de exemplo.
5. Efeitos colaterais externos (e-mail de mudança de status) passam a ser
   disparados **após o commit**.

## Alternativas consideradas

- **Publicar após o commit** (`@TransactionalEventListener(AFTER_COMMIT)`)
  **sem outbox**: evita o evento fantasma, mas uma queda entre o commit e a
  publicação perde a mensagem.
- **CDC (Debezium) lendo o log do banco**: outbox sem polling e com menor
  latência, mas exige Kafka Connect ou infraestrutura adicional e não cobre
  o MongoDB do Billing da mesma forma. O polling agendado é suficiente para
  o volume do projeto.
- **Idempotência só pela máquina de estados** (ignorar evento que não
  corresponde à etapa): ajuda, mas não protege comandos com efeito externo
  (cobrança, estorno) nem eventos que chegam duplicados na etapa certa. O
  inbox é a garantia explícita; a máquina de estados é a segunda barreira.

## Consequências

- Entrega "pelo menos uma vez" + inbox = processamento efetivamente uma vez.
- Latência adicional entre o commit e a publicação (intervalo do
  publicador), aceitável para um fluxo de minutos ou horas.
- Tabelas/coleções `outbox` e `inbox` em cada serviço, com limpeza
  periódica a definir; métricas de pendências do outbox ajudam a detectar
  publicador parado.
- No Billing (MongoDB), a atomicidade estado + outbox depende da escolha da
  Etapa 4.1 (ADR 0007).
- Testes de integração (Testcontainers) cobrem: gravar no outbox → a
  mensagem chega na fila; mensagem duplicada → processada uma vez.
