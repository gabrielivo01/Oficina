# ADR 0010: Evento StatusOrdemServicoAlterado e notificação ao cliente a partir dele

- Status: Aceita
- Data: 2026-11-15

## Contexto

O ADR 0009 define outbox, inbox e que o e-mail de mudança de status deve sair
**depois do commit**. Na Fase 2 o OS Service ainda não tem a Saga (Fase 5),
então nenhum comando ou evento da seção 3.2 do plano é publicado por ele; mesmo
assim a infraestrutura de mensageria (outbox, publicador com confirms, listener
com inbox, retry e DLQ) e o tracing ponta a ponta precisam ser exercitados por
um fluxo real. Até aqui o e-mail era enviado de dentro da transação, por
chamada direta à `StatusNotificationPort`.

A topologia (ADR 0006, `definitions.json`) liga `oficina.eventos` à fila
`os.saga.eventos` com binding `#`: tudo o que for publicado em
`oficina.eventos` chega ao consumidor do OS Service.

## Decisão

1. Toda transição de status da OS (não a abertura) registra o evento de domínio
   `StatusOrdemServicoAlterado` no agregado; o service o grava no **outbox** na
   mesma transação e ele é publicado em `oficina.eventos` com routing key
   `os.status-alterado` (contrato em `contratos_mensagens.md`).
2. O próprio OS Service consome esse evento em `os.saga.eventos` (com inbox) e é
   esse manipulador que envia a notificação ao cliente via
   `StatusNotificationPort`. O service de OS não chama mais a porta de
   notificação.
3. O consumidor de `os.saga.eventos` despacha por `type` para manipuladores
   registrados (`ManipuladorMensagem`); a Saga (Fase 5) entra como novos
   manipuladores na mesma fila. Tipo sem manipulador é rejeitado direto para a
   DLQ (não entra no inbox, pode ser reprocessado depois).

## Alternativas consideradas

- **`@TransactionalEventListener(AFTER_COMMIT)` chamando a porta de e-mail**:
  mais simples, mas um e-mail que falha depois do commit se perde (sem retry nem
  DLQ) e nada da mensageria seria exercitado antes da Fase 5.
- **Publicar `SolicitarDiagnostico` na abertura da OS já na Fase 2**: anteciparia
  a Saga (Etapa 5.2) e mandaria comandos ao Execution Service sem orquestrador
  para tratar as respostas.
- **Fila própria para notificações**: exigiria mudar a topologia da Fase 1; o
  binding `#` já entrega o evento ao OS Service.

## Consequências

- O e-mail sai só após o commit e com retry (3 tentativas com backoff) e DLQ; o
  inbox evita reenvio em mensagens duplicadas. Entrega "pelo menos uma vez": se
  o envio funcionar e o commit do inbox falhar, o e-mail pode repetir.
- Com o RabbitMQ fora, a mudança de status continua funcionando; os eventos
  esperam no outbox e as notificações atrasam até o broker voltar.
- A recusa de orçamento, que não muda o status até a Fase 5, deixa de gerar
  e-mail. O cancelamento passa a gerar.
- Outros serviços podem assinar `os.status-alterado` em `oficina.eventos` sem
  mudança no OS Service.
