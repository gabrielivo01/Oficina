# Catálogo de contratos de mensagens

Contrato oficial das mensagens trocadas pelos serviços da Fase 4 (OS Service, Execution Service, Billing
Service) via RabbitMQ. **Não há biblioteca compartilhada** (regra 6 do `plano_fase4.md`): cada serviço define os
próprios DTOs e tem um **teste de contrato** que desserializa os exemplos deste catálogo. Os exemplos JSON
abaixo são idênticos aos de `src/test/resources/contratos/<type>.json`; o teste `ContratosMensagensTest` falha
se divergirem ou se faltar algum.

Fluxo da Saga: seção 3.2 do `plano_fase4.md`; topologia: seção 3.3 e
`oficina-infra-k8s/local/rabbitmq/definitions.json`; decisões: ADRs 0005, 0006, 0009, 0010 e 0011.

## Convenções

### Envelope (todas as mensagens)

| Campo | Tipo | Regra |
|---|---|---|
| `messageId` | string (UUID) | Único por mensagem; chave do **inbox** do consumidor (duplicata é ignorada). Reenvio da mesma mensagem mantém o id. |
| `type` | string | Nome do comando/evento (as seções abaixo). O consumidor despacha por ele. |
| `sagaId` | string (UUID) | Id da saga = id da OS. |
| `osId` | string (UUID) | Id da OS no OS Service. |
| `occurredAt` | string ISO-8601 UTC | Instante do fato (evento) ou da decisão de enviar (comando). |
| `payload` | objeto | Específico do `type` (tabelas abaixo). |

### Propriedades e headers AMQP

- `message_id` = `messageId`, `type` = `type`, `content_type` = `application/json`, `content_encoding` = `UTF-8`,
  `delivery_mode` = 2 (persistente), `app_id` = serviço produtor (ex.: `oficina-os-service`).
- `traceparent` (W3C Trace Context) nos headers, injetado pela observação do Spring AMQP. Quem publica via
  outbox guarda o `traceparent` de quem gravou a mensagem e continua esse trace (ADR 0011).

### Roteamento

- **Comandos**: exchange `oficina.comandos` (direct); routing key = nome da fila do serviço destino
  (`execution.comandos` ou `billing.comandos`).
- **Eventos**: exchange `oficina.eventos` (topic); routing key `<servico>.<evento-em-kebab-case>`
  (`execution.*`, `billing.*`, `os.*`). O OS Service consome tudo pela fila `os.saga.eventos` (binding `#`).
- Toda fila tem DLQ (`<fila>.dlq` via `oficina.dlx`) e `x-delivery-limit: 3`; o consumidor tenta 3 vezes com
  backoff antes de rejeitar. Tipo desconhecido para o consumidor vai direto para a DLQ.

### Tipos de dados e compatibilidade

- Ids: UUID em texto. Datas: ISO-8601 em UTC (`Z`). Valores monetários: número decimal em reais com 2 casas.
- Enums em texto maiúsculo (`SERVICO`, `PECA`, `USUARIO`, `CLIENTE`...).
- Evolução compatível: campos novos só opcionais; consumidores ignoram campos desconhecidos. Mudança
  incompatível = novo `type` (ex.: `DiagnosticoConcluidoV2`), com convivência até todos migrarem.
- Produtores usam o **Transactional Outbox**; consumidores são **idempotentes** (inbox + máquina de estados).

## Resumo

| `type` | Categoria | Produtor | Consumidor | Exchange | Routing key | Fila | Passo (3.2) |
|---|---|---|---|---|---|---|---|
| `SolicitarDiagnostico` | comando | OS | Execution | `oficina.comandos` | `execution.comandos` | `execution.comandos` | 1 |
| `DiagnosticoIniciado` | evento | Execution | OS | `oficina.eventos` | `execution.diagnostico-iniciado` | `os.saga.eventos` | 2 |
| `DiagnosticoConcluido` | evento | Execution | OS | `oficina.eventos` | `execution.diagnostico-concluido` | `os.saga.eventos` | 3 |
| `GerarOrcamento` | comando | OS | Billing | `oficina.comandos` | `billing.comandos` | `billing.comandos` | 3 |
| `OrcamentoGerado` | evento | Billing | OS | `oficina.eventos` | `billing.orcamento-gerado` | `os.saga.eventos` | 4 |
| `OrcamentoAprovado` | evento | Billing | OS | `oficina.eventos` | `billing.orcamento-aprovado` | `os.saga.eventos` | 5a |
| `CriarCobranca` | comando | OS | Billing | `oficina.comandos` | `billing.comandos` | `billing.comandos` | 5a |
| `OrcamentoRecusado` | evento | Billing | OS | `oficina.eventos` | `billing.orcamento-recusado` | `os.saga.eventos` | 5b |
| `LiberarReservaPecas` | comando (compensação) | OS | Execution | `oficina.comandos` | `execution.comandos` | `execution.comandos` | 5b, 7b, 8b |
| `ReservaPecasLiberada` | evento | Execution | OS | `oficina.eventos` | `execution.reserva-pecas-liberada` | `os.saga.eventos` | 5b, 7b, 8b |
| `CobrancaCriada` | evento | Billing | OS | `oficina.eventos` | `billing.cobranca-criada` | `os.saga.eventos` | 6 |
| `CobrancaCancelada` | evento | Billing | OS | `oficina.eventos` | `billing.cobranca-cancelada` | `os.saga.eventos` | 7b (confirmação da compensação) |
| `PagamentoConfirmado` | evento | Billing | OS | `oficina.eventos` | `billing.pagamento-confirmado` | `os.saga.eventos` | 7a |
| `IniciarExecucao` | comando | OS | Execution | `oficina.comandos` | `execution.comandos` | `execution.comandos` | 7a |
| `CancelarExecucao` | comando (compensação) | OS | Execution | `oficina.comandos` | `execution.comandos` | `execution.comandos` | cancelamento manual |
| `PagamentoRejeitado` | evento | Billing | OS | `oficina.eventos` | `billing.pagamento-rejeitado` | `os.saga.eventos` | 7b |
| `CancelarCobranca` | comando (compensação) | OS | Billing | `oficina.comandos` | `billing.comandos` | `billing.comandos` | 7b |
| `ExecucaoIniciada` | evento | Execution | OS | `oficina.eventos` | `execution.execucao-iniciada` | `os.saga.eventos` | 8a |
| `ExecucaoRecusada` | evento | Execution | OS | `oficina.eventos` | `execution.execucao-recusada` | `os.saga.eventos` | 8b |
| `ExecucaoCancelada` | evento | Execution | OS | `oficina.eventos` | `execution.execucao-cancelada` | `os.saga.eventos` | confirmação de cancelamento manual |
| `EstornarPagamento` | comando (compensação) | OS | Billing | `oficina.comandos` | `billing.comandos` | `billing.comandos` | 8b |
| `PagamentoEstornado` | evento | Billing | OS | `oficina.eventos` | `billing.pagamento-estornado` | `os.saga.eventos` | 8b |
| `ExecucaoFinalizada` | evento | Execution | OS | `oficina.eventos` | `execution.execucao-finalizada` | `os.saga.eventos` | 9 |
| `StatusOrdemServicoAlterado` | evento (fora da Saga) | OS | OS (notificação) e quem assinar | `oficina.eventos` | `os.status-alterado` | `os.saga.eventos` | toda transição de status (ADR 0010) |

Os contratos acima cobrem os três serviços, incluindo a orquestração do OS Service e as confirmações de
compensação do Execution e Billing.

## Comandos

### `SolicitarDiagnostico`

OS Service → Execution Service, ao abrir a OS (passo 1). O Execution cria a tarefa de diagnóstico na fila.
Idempotente por `osId`: um segundo comando para a mesma OS não cria outra tarefa.

| Campo | Tipo | Obrigatório | Descrição |
|---|---|---|---|
| `clienteId` | UUID | sim | Cliente dono da OS. |
| `veiculo.id` | UUID | sim | Veículo da OS. |
| `veiculo.placa` | string | sim | Placa (também usada pela injeção de falha por placa do Execution). |
| `veiculo.marca`, `veiculo.modelo` | string | sim | Identificação para o mecânico. |
| `veiculo.ano` | inteiro | não | Ano do veículo. |
| `observacao` | string | não | Relato do cliente na abertura. |

```json
{
  "messageId": "5e7d0a10-0000-4000-8000-000000000001",
  "type": "SolicitarDiagnostico",
  "sagaId": "8d3b7c1e-2f4a-4e6b-9c0d-5a1b2c3d4e5f",
  "osId": "8d3b7c1e-2f4a-4e6b-9c0d-5a1b2c3d4e5f",
  "occurredAt": "2026-11-15T13:00:00Z",
  "payload": {
    "clienteId": "1a95441a-321b-428e-913a-d44a4033ad81",
    "veiculo": {
      "id": "99a54e00-6bcf-4075-90c5-1a145d14adb2",
      "placa": "ABC1D23",
      "marca": "Fiat",
      "modelo": "Uno",
      "ano": 2020
    },
    "observacao": "Barulho na suspensão dianteira"
  }
}
```

### `GerarOrcamento`

OS Service → Billing Service, ao receber `DiagnosticoConcluido` (passo 3). O Billing cria o orçamento com o
snapshot dos itens, notifica o cliente e responde `OrcamentoGerado`. Idempotente por `osId`.

| Campo | Tipo | Obrigatório | Descrição |
|---|---|---|---|
| `cliente.id`, `cliente.nome`, `cliente.cpf`, `cliente.email` | string | sim | Dados para o orçamento e a notificação. Clientes existentes sem e-mail devem atualizá-lo antes de abrir uma OS que gere orçamento. |
| `cliente.telefone` | string | não | Contato. |
| `veiculo.placa`, `veiculo.marca`, `veiculo.modelo` | string | sim | Identificação do veículo no orçamento. |
| `veiculo.ano` | inteiro | não | Ano. |
| `itens[]` | lista | sim (≥ 1) | Snapshot do `DiagnosticoConcluido` (mesmo formato de item). |
| `valorTotal` | decimal | sim | Soma de `itens[].valorTotal`. |

```json
{
  "messageId": "5e7d0a10-0000-4000-8000-000000000004",
  "type": "GerarOrcamento",
  "sagaId": "8d3b7c1e-2f4a-4e6b-9c0d-5a1b2c3d4e5f",
  "osId": "8d3b7c1e-2f4a-4e6b-9c0d-5a1b2c3d4e5f",
  "occurredAt": "2026-11-15T14:00:01Z",
  "payload": {
    "cliente": {
      "id": "1a95441a-321b-428e-913a-d44a4033ad81",
      "nome": "Maria Souza",
      "cpf": "52998224725",
      "telefone": "11999999999",
      "email": "maria.souza@example.com"
    },
    "veiculo": {
      "placa": "ABC1D23",
      "marca": "Fiat",
      "modelo": "Uno",
      "ano": 2020
    },
    "itens": [
      {
        "tipo": "SERVICO",
        "descricao": "Troca de amortecedores dianteiros",
        "pecaId": null,
        "quantidade": 1,
        "valorUnitario": 150.00,
        "valorTotal": 150.00
      },
      {
        "tipo": "PECA",
        "descricao": "Amortecedor dianteiro",
        "pecaId": "c0a8012e-0003-4000-8000-000000000003",
        "quantidade": 2,
        "valorUnitario": 220.25,
        "valorTotal": 440.50
      }
    ],
    "valorTotal": 590.50
  }
}
```

### `CriarCobranca`

OS Service → Billing Service, ao receber `OrcamentoAprovado` (passo 5a). O Billing cria a cobrança no gateway
(Mercado Pago ou fake) e responde `CobrancaCriada`. Idempotente por `osId` + `orcamentoId`.

| Campo | Tipo | Obrigatório | Descrição |
|---|---|---|---|
| `orcamentoId` | UUID | sim | Orçamento aprovado. |
| `valor` | decimal | sim | Valor a cobrar (= total do orçamento). |
| `descricao` | string | sim | Texto exibido ao pagador. |
| `prazoPagamento` | ISO-8601 | sim | Limite para pagar; depois o orquestrador compensa (timeout, passo 7b). |

```json
{
  "messageId": "5e7d0a10-0000-4000-8000-000000000008",
  "type": "CriarCobranca",
  "sagaId": "8d3b7c1e-2f4a-4e6b-9c0d-5a1b2c3d4e5f",
  "osId": "8d3b7c1e-2f4a-4e6b-9c0d-5a1b2c3d4e5f",
  "occurredAt": "2026-11-15T15:00:02Z",
  "payload": {
    "orcamentoId": "b1110000-0004-4000-8000-000000000004",
    "valor": 590.50,
    "descricao": "OS 8d3b7c1e - Troca de amortecedores dianteiros",
    "prazoPagamento": "2026-11-15T15:30:02Z"
  }
}
```

### `IniciarExecucao`

OS Service → Execution Service, ao receber `PagamentoConfirmado` (passo 7a). O Execution consome a reserva
(baixa definitiva) e cria a tarefa de reparo → `ExecucaoIniciada`; ou recusa (ex.: injeção de falha
`app.simulacao.recusar-inicio-execucao`) → `ExecucaoRecusada`. Idempotente por `osId`.

| Campo | Tipo | Obrigatório | Descrição |
|---|---|---|---|
| `reservaId` | UUID | sim | Reserva criada no `DiagnosticoConcluido`. |
| `cobrancaId` | UUID | sim | Cobrança paga (rastreabilidade). |
| `placa` | string | sim | Placa do veículo (injeção de falha por placa). |

```json
{
  "messageId": "5e7d0a10-0000-4000-8000-000000000012",
  "type": "IniciarExecucao",
  "sagaId": "8d3b7c1e-2f4a-4e6b-9c0d-5a1b2c3d4e5f",
  "osId": "8d3b7c1e-2f4a-4e6b-9c0d-5a1b2c3d4e5f",
  "occurredAt": "2026-11-15T15:10:02Z",
  "payload": {
    "reservaId": "c0a8012e-0002-4000-8000-000000000002",
    "cobrancaId": "b1110000-0005-4000-8000-000000000005",
    "placa": "ABC1D23"
  }
}
```

### `CancelarExecucao`

OS Service → Execution Service. **Compensação** de cancelamento manual após o pagamento e antes da conclusão do
reparo. Execution cancela uma tarefa na fila ou em andamento e repõe o estoque consumido; só então publica
`ExecucaoCancelada`. Se o reparo já tiver sido concluído, republica `ExecucaoFinalizada` e a Saga mantém a OS
finalizada em vez de reembolsar uma execução concluída.

| Campo | Tipo | Obrigatório | Descrição |
|---|---|---|---|
| `reservaId` | UUID | sim | Reserva a devolver ao estoque. |
| `motivo` | enum | sim | `CANCELAMENTO`. |

```json
{
  "messageId": "5e7d0a10-0000-4000-8000-000000000023",
  "type": "CancelarExecucao",
  "sagaId": "8d3b7c1e-2f4a-4e6b-9c0d-5a1b2c3d4e5f",
  "osId": "8d3b7c1e-2f4a-4e6b-9c0d-5a1b2c3d4e5f",
  "occurredAt": "2026-11-15T15:25:00Z",
  "payload": {
    "reservaId": "c0a8012e-0002-4000-8000-000000000002",
    "motivo": "CANCELAMENTO"
  }
}
```

### `LiberarReservaPecas`

OS Service → Execution Service. **Compensação** (passos 5b, 7b e 8b): devolve ao estoque as peças reservadas
da OS. Idempotente: liberar uma reserva já liberada (ou inexistente) responde `ReservaPecasLiberada` sem efeito.

| Campo | Tipo | Obrigatório | Descrição |
|---|---|---|---|
| `reservaId` | UUID | não | Reserva a liberar; ausente = a reserva da `osId`. |
| `motivo` | enum | sim | `ORCAMENTO_RECUSADO`, `PAGAMENTO_REJEITADO`, `PRAZO_PAGAMENTO_EXPIRADO`, `EXECUCAO_RECUSADA` ou `CANCELAMENTO`. |

```json
{
  "messageId": "5e7d0a10-0000-4000-8000-000000000016",
  "type": "LiberarReservaPecas",
  "sagaId": "8d3b7c1e-2f4a-4e6b-9c0d-5a1b2c3d4e5f",
  "osId": "8d3b7c1e-2f4a-4e6b-9c0d-5a1b2c3d4e5f",
  "occurredAt": "2026-11-15T15:00:01Z",
  "payload": {
    "reservaId": "c0a8012e-0002-4000-8000-000000000002",
    "motivo": "ORCAMENTO_RECUSADO"
  }
}
```

### `CancelarCobranca`

OS Service → Billing Service. **Compensação** (passo 7b): cancela a cobrança ainda pendente (pagamento
rejeitado ou prazo expirado). Idempotente; cobrança já cancelada ou rejeitada não muda. Em seguida o
orquestrador envia `LiberarReservaPecas`.

| Campo | Tipo | Obrigatório | Descrição |
|---|---|---|---|
| `cobrancaId` | UUID | sim | Cobrança a cancelar. |
| `motivo` | enum | sim | `PAGAMENTO_REJEITADO`, `PRAZO_PAGAMENTO_EXPIRADO` ou `CANCELAMENTO`. |

```json
{
  "messageId": "5e7d0a10-0000-4000-8000-000000000018",
  "type": "CancelarCobranca",
  "sagaId": "8d3b7c1e-2f4a-4e6b-9c0d-5a1b2c3d4e5f",
  "osId": "8d3b7c1e-2f4a-4e6b-9c0d-5a1b2c3d4e5f",
  "occurredAt": "2026-11-15T15:30:05Z",
  "payload": {
    "cobrancaId": "b1110000-0005-4000-8000-000000000005",
    "motivo": "PRAZO_PAGAMENTO_EXPIRADO"
  }
}
```

### `EstornarPagamento`

OS Service → Billing Service. **Compensação** (passo 8b): estorna o pagamento confirmado quando a execução é
recusada; o Billing responde `PagamentoEstornado`. Idempotente: estorno repetido não chama o gateway de novo.

| Campo | Tipo | Obrigatório | Descrição |
|---|---|---|---|
| `cobrancaId` | UUID | sim | Cobrança paga a estornar. |
| `motivo` | enum | sim | `EXECUCAO_RECUSADA` ou `CANCELAMENTO`. |

```json
{
  "messageId": "5e7d0a10-0000-4000-8000-000000000019",
  "type": "EstornarPagamento",
  "sagaId": "8d3b7c1e-2f4a-4e6b-9c0d-5a1b2c3d4e5f",
  "osId": "8d3b7c1e-2f4a-4e6b-9c0d-5a1b2c3d4e5f",
  "occurredAt": "2026-11-15T15:20:02Z",
  "payload": {
    "cobrancaId": "b1110000-0005-4000-8000-000000000005",
    "motivo": "EXECUCAO_RECUSADA"
  }
}
```

## Eventos

### `DiagnosticoIniciado`

Execution Service → OS Service, quando o mecânico inicia o diagnóstico (passo 2). A OS vai para
`EM_DIAGNOSTICO`.

| Campo | Tipo | Obrigatório | Descrição |
|---|---|---|---|
| `tarefaId` | UUID | sim | Tarefa de diagnóstico no Execution. |
| `mecanico` | string | não | Login de quem iniciou. |
| `iniciadoEm` | ISO-8601 | sim | Início do diagnóstico. |

```json
{
  "messageId": "5e7d0a10-0000-4000-8000-000000000002",
  "type": "DiagnosticoIniciado",
  "sagaId": "8d3b7c1e-2f4a-4e6b-9c0d-5a1b2c3d4e5f",
  "osId": "8d3b7c1e-2f4a-4e6b-9c0d-5a1b2c3d4e5f",
  "occurredAt": "2026-11-15T13:10:00Z",
  "payload": {
    "tarefaId": "c0a8012e-0001-4000-8000-000000000001",
    "mecanico": "joao.mecanico",
    "iniciadoEm": "2026-11-15T13:10:00Z"
  }
}
```

### `DiagnosticoConcluido`

Execution Service → OS Service, ao concluir o diagnóstico com os itens e **estoque já reservado** (passo 3).
O OS Service guarda o snapshot dos itens e envia `GerarOrcamento`. Itens ficam imutáveis no Execution.

| Campo | Tipo | Obrigatório | Descrição |
|---|---|---|---|
| `tarefaId` | UUID | sim | Tarefa de diagnóstico. |
| `reservaId` | UUID | sim | Reserva de peças criada (pode não ter itens `PECA`). |
| `itens[].tipo` | enum | sim | `SERVICO` ou `PECA`. |
| `itens[].descricao` | string | sim | Descrição do item. |
| `itens[].pecaId` | UUID ou `null` | só `PECA` | Peça do catálogo do Execution. |
| `itens[].quantidade` | inteiro > 0 | sim | `SERVICO` = 1. |
| `itens[].valorUnitario`, `itens[].valorTotal` | decimal | sim | Preço da peça vem do catálogo; `valorTotal` = unitário × quantidade. |
| `valorTotal` | decimal | sim | Soma dos itens. |
| `concluidoEm` | ISO-8601 | sim | Conclusão do diagnóstico. |

```json
{
  "messageId": "5e7d0a10-0000-4000-8000-000000000003",
  "type": "DiagnosticoConcluido",
  "sagaId": "8d3b7c1e-2f4a-4e6b-9c0d-5a1b2c3d4e5f",
  "osId": "8d3b7c1e-2f4a-4e6b-9c0d-5a1b2c3d4e5f",
  "occurredAt": "2026-11-15T14:00:00Z",
  "payload": {
    "tarefaId": "c0a8012e-0001-4000-8000-000000000001",
    "reservaId": "c0a8012e-0002-4000-8000-000000000002",
    "itens": [
      {
        "tipo": "SERVICO",
        "descricao": "Troca de amortecedores dianteiros",
        "pecaId": null,
        "quantidade": 1,
        "valorUnitario": 150.00,
        "valorTotal": 150.00
      },
      {
        "tipo": "PECA",
        "descricao": "Amortecedor dianteiro",
        "pecaId": "c0a8012e-0003-4000-8000-000000000003",
        "quantidade": 2,
        "valorUnitario": 220.25,
        "valorTotal": 440.50
      }
    ],
    "valorTotal": 590.50,
    "concluidoEm": "2026-11-15T14:00:00Z"
  }
}
```

### `OrcamentoGerado`

Billing Service → OS Service, após criar o orçamento e notificar o cliente (passo 4). A OS vai para
`AGUARDANDO_APROVACAO`.

| Campo | Tipo | Obrigatório | Descrição |
|---|---|---|---|
| `orcamentoId` | UUID | sim | Orçamento no Billing. |
| `valorTotal` | decimal | sim | Valor do orçamento. |
| `geradoEm` | ISO-8601 | sim | Criação do orçamento. |

```json
{
  "messageId": "5e7d0a10-0000-4000-8000-000000000005",
  "type": "OrcamentoGerado",
  "sagaId": "8d3b7c1e-2f4a-4e6b-9c0d-5a1b2c3d4e5f",
  "osId": "8d3b7c1e-2f4a-4e6b-9c0d-5a1b2c3d4e5f",
  "occurredAt": "2026-11-15T14:00:05Z",
  "payload": {
    "orcamentoId": "b1110000-0004-4000-8000-000000000004",
    "valorTotal": 590.50,
    "geradoEm": "2026-11-15T14:00:05Z"
  }
}
```

### `OrcamentoAprovado`

Billing Service → OS Service, quando o cliente (token `tipo=CLIENTE`) ou o atendente aprova (passo 5a). O
orquestrador envia `CriarCobranca`.

| Campo | Tipo | Obrigatório | Descrição |
|---|---|---|---|
| `orcamentoId` | UUID | sim | Orçamento aprovado. |
| `valorTotal` | decimal | sim | Valor aprovado (base da cobrança). |
| `respondidoPor.tipo` | enum | sim | `CLIENTE` ou `USUARIO` (staff), conforme o token. |
| `respondidoPor.identificador` | string | sim | CPF (cliente) ou login (staff). |
| `observacao` | string | não | Comentário da resposta. |
| `respondidoEm` | ISO-8601 | sim | Momento da resposta. |

```json
{
  "messageId": "5e7d0a10-0000-4000-8000-000000000006",
  "type": "OrcamentoAprovado",
  "sagaId": "8d3b7c1e-2f4a-4e6b-9c0d-5a1b2c3d4e5f",
  "osId": "8d3b7c1e-2f4a-4e6b-9c0d-5a1b2c3d4e5f",
  "occurredAt": "2026-11-15T15:00:00Z",
  "payload": {
    "orcamentoId": "b1110000-0004-4000-8000-000000000004",
    "valorTotal": 590.50,
    "respondidoPor": {
      "tipo": "CLIENTE",
      "identificador": "52998224725"
    },
    "observacao": "Pode fazer",
    "respondidoEm": "2026-11-15T15:00:00Z"
  }
}
```

### `OrcamentoRecusado`

Billing Service → OS Service (passo 5b). O orquestrador compensa com `LiberarReservaPecas`; a OS vai para
`CANCELADA` após `ReservaPecasLiberada`.

| Campo | Tipo | Obrigatório | Descrição |
|---|---|---|---|
| `orcamentoId` | UUID | sim | Orçamento recusado. |
| `valorTotal` | decimal | não | Valor do orçamento recusado (informativo; a Saga não usa). |
| `respondidoPor.tipo`, `respondidoPor.identificador` | — | sim | Como em `OrcamentoAprovado`. |
| `observacao` | string | não | Motivo informado. |
| `respondidoEm` | ISO-8601 | sim | Momento da resposta. |

```json
{
  "messageId": "5e7d0a10-0000-4000-8000-000000000007",
  "type": "OrcamentoRecusado",
  "sagaId": "8d3b7c1e-2f4a-4e6b-9c0d-5a1b2c3d4e5f",
  "osId": "8d3b7c1e-2f4a-4e6b-9c0d-5a1b2c3d4e5f",
  "occurredAt": "2026-11-15T15:00:00Z",
  "payload": {
    "orcamentoId": "b1110000-0004-4000-8000-000000000004",
    "valorTotal": 590.50,
    "respondidoPor": {
      "tipo": "USUARIO",
      "identificador": "atendente"
    },
    "observacao": "Cliente achou caro",
    "respondidoEm": "2026-11-15T15:00:00Z"
  }
}
```

### `CobrancaCriada`

Billing Service → OS Service, com o link/QR do Mercado Pago (passo 6). A OS vai para `AGUARDANDO_PAGAMENTO`.

| Campo | Tipo | Obrigatório | Descrição |
|---|---|---|---|
| `cobrancaId` | UUID | sim | Cobrança no Billing. |
| `orcamentoId` | UUID | sim | Orçamento cobrado. |
| `idExterno` | string | sim | Id no gateway (preferência do Mercado Pago ou id do fake). |
| `valor` | decimal | sim | Valor cobrado. |
| `linkPagamento` | URL | sim | Link de pagamento. |
| `qrCode` | string ou `null` | não | Código PIX copia-e-cola, quando houver. |
| `expiraEm` | ISO-8601 | sim | Prazo de pagamento. |

```json
{
  "messageId": "5e7d0a10-0000-4000-8000-000000000009",
  "type": "CobrancaCriada",
  "sagaId": "8d3b7c1e-2f4a-4e6b-9c0d-5a1b2c3d4e5f",
  "osId": "8d3b7c1e-2f4a-4e6b-9c0d-5a1b2c3d4e5f",
  "occurredAt": "2026-11-15T15:00:04Z",
  "payload": {
    "cobrancaId": "b1110000-0005-4000-8000-000000000005",
    "orcamentoId": "b1110000-0004-4000-8000-000000000004",
    "idExterno": "1234567890-abcd1234-ef56-7890-ab12-cd34ef56ab78",
    "valor": 590.50,
    "linkPagamento": "https://sandbox.mercadopago.com.br/checkout/v1/redirect?pref_id=1234567890-abcd1234-ef56-7890-ab12-cd34ef56ab78",
    "qrCode": null,
    "expiraEm": "2026-11-15T15:30:02Z"
  }
}
```

### `CobrancaCancelada`

Billing Service → OS Service, após cancelar uma cobrança pendente (passo 7b). A confirmação permite ao orquestrador
liberar a reserva de peças apenas depois de invalidar o link de pagamento. Se o pagamento já tiver sido aprovado, o
Billing publica `PagamentoConfirmado` em vez deste evento para que a Saga execute o estorno.

| Campo | Tipo | Obrigatório | Descrição |
|---|---|---|---|
| `cobrancaId` | UUID | sim | Cobrança cancelada. |
| `motivo` | string | sim | Motivo do cancelamento. |
| `canceladaEm` | ISO-8601 | sim | Confirmação do cancelamento. |

```json
{
  "messageId": "5e7d0a10-0000-4000-8000-000000000022",
  "type": "CobrancaCancelada",
  "sagaId": "8d3b7c1e-2f4a-4e6b-9c0d-5a1b2c3d4e5f",
  "osId": "8d3b7c1e-2f4a-4e6b-9c0d-5a1b2c3d4e5f",
  "occurredAt": "2026-11-15T15:15:00Z",
  "payload": {
    "cobrancaId": "b1110000-0005-4000-8000-000000000005",
    "motivo": "PRAZO_PAGAMENTO_EXPIRADO",
    "canceladaEm": "2026-11-15T15:15:00Z"
  }
}
```

### `PagamentoConfirmado`

Billing Service → OS Service, após confirmar o pagamento **consultando o gateway** (passo 7a). O orquestrador
envia `IniciarExecucao`.

| Campo | Tipo | Obrigatório | Descrição |
|---|---|---|---|
| `cobrancaId` | UUID | sim | Cobrança paga. |
| `pagamentoIdExterno` | string | sim | Id do pagamento no gateway (necessário para estorno). |
| `valor` | decimal | sim | Valor pago. |
| `formaPagamento` | string | sim | Ex.: `PIX`, `CARTAO`. |
| `confirmadoEm` | ISO-8601 | sim | Confirmação. |

```json
{
  "messageId": "5e7d0a10-0000-4000-8000-000000000010",
  "type": "PagamentoConfirmado",
  "sagaId": "8d3b7c1e-2f4a-4e6b-9c0d-5a1b2c3d4e5f",
  "osId": "8d3b7c1e-2f4a-4e6b-9c0d-5a1b2c3d4e5f",
  "occurredAt": "2026-11-15T15:10:00Z",
  "payload": {
    "cobrancaId": "b1110000-0005-4000-8000-000000000005",
    "pagamentoIdExterno": "98765432101",
    "valor": 590.50,
    "formaPagamento": "PIX",
    "confirmadoEm": "2026-11-15T15:10:00Z"
  }
}
```

### `PagamentoRejeitado`

Billing Service → OS Service (passo 7b). O orquestrador compensa: `CancelarCobranca` (se pendente) →
`LiberarReservaPecas` → OS `CANCELADA`.

| Campo | Tipo | Obrigatório | Descrição |
|---|---|---|---|
| `cobrancaId` | UUID | sim | Cobrança. |
| `pagamentoIdExterno` | string | não | Id da tentativa no gateway, se houver. |
| `motivo` | string | sim | Código/motivo do gateway. |
| `rejeitadoEm` | ISO-8601 | sim | Rejeição. |

```json
{
  "messageId": "5e7d0a10-0000-4000-8000-000000000011",
  "type": "PagamentoRejeitado",
  "sagaId": "8d3b7c1e-2f4a-4e6b-9c0d-5a1b2c3d4e5f",
  "osId": "8d3b7c1e-2f4a-4e6b-9c0d-5a1b2c3d4e5f",
  "occurredAt": "2026-11-15T15:10:00Z",
  "payload": {
    "cobrancaId": "b1110000-0005-4000-8000-000000000005",
    "pagamentoIdExterno": "98765432101",
    "motivo": "cc_rejected_insufficient_amount",
    "rejeitadoEm": "2026-11-15T15:10:00Z"
  }
}
```

### `ExecucaoIniciada`

Execution Service → OS Service, quando a reserva vira **baixa definitiva** e a tarefa de reparo é criada
(passo 8a). A OS vai para `EM_EXECUCAO`.

| Campo | Tipo | Obrigatório | Descrição |
|---|---|---|---|
| `tarefaId` | UUID | sim | Tarefa de reparo. |
| `reservaId` | UUID | sim | Reserva consumida. |
| `iniciadaEm` | ISO-8601 | sim | Início da execução. |

```json
{
  "messageId": "5e7d0a10-0000-4000-8000-000000000013",
  "type": "ExecucaoIniciada",
  "sagaId": "8d3b7c1e-2f4a-4e6b-9c0d-5a1b2c3d4e5f",
  "osId": "8d3b7c1e-2f4a-4e6b-9c0d-5a1b2c3d4e5f",
  "occurredAt": "2026-11-15T15:20:00Z",
  "payload": {
    "tarefaId": "c0a8012e-0006-4000-8000-000000000006",
    "reservaId": "c0a8012e-0002-4000-8000-000000000002",
    "iniciadaEm": "2026-11-15T15:20:00Z"
  }
}
```

### `ExecucaoRecusada`

Execution Service → OS Service, quando não é possível iniciar a execução (passo 8b; inclui a injeção de falha
da demonstração). A reserva **não** é consumida. O orquestrador compensa: `EstornarPagamento` → (após
`PagamentoEstornado`) `LiberarReservaPecas` → OS `CANCELADA`.

| Campo | Tipo | Obrigatório | Descrição |
|---|---|---|---|
| `reservaId` | UUID | sim | Reserva mantida, a ser liberada na compensação. |
| `motivo` | string | sim | Motivo da recusa. |
| `recusadaEm` | ISO-8601 | sim | Recusa. |

```json
{
  "messageId": "5e7d0a10-0000-4000-8000-000000000014",
  "type": "ExecucaoRecusada",
  "sagaId": "8d3b7c1e-2f4a-4e6b-9c0d-5a1b2c3d4e5f",
  "osId": "8d3b7c1e-2f4a-4e6b-9c0d-5a1b2c3d4e5f",
  "occurredAt": "2026-11-15T15:20:00Z",
  "payload": {
    "reservaId": "c0a8012e-0002-4000-8000-000000000002",
    "motivo": "Execução recusada pela simulação de falha (app.simulacao.recusar-inicio-execucao)",
    "recusadaEm": "2026-11-15T15:20:00Z"
  }
}
```

### `ExecucaoCancelada`

Execution Service → OS Service, após cancelar a tarefa de reparo e devolver ao estoque a reserva consumida. A Saga
prossegue com `EstornarPagamento`; depois da confirmação do estorno, marca a OS como `CANCELADA`. Se a tarefa já
estiver concluída, Execution publica `ExecucaoFinalizada` em vez deste evento.

| Campo | Tipo | Obrigatório | Descrição |
|---|---|---|---|
| `tarefaId` | UUID | sim | Tarefa de reparo cancelada. |
| `reservaId` | UUID | sim | Reserva devolvida ao estoque. |
| `canceladaEm` | ISO-8601 | sim | Confirmação do cancelamento. |

```json
{
  "messageId": "5e7d0a10-0000-4000-8000-000000000024",
  "type": "ExecucaoCancelada",
  "sagaId": "8d3b7c1e-2f4a-4e6b-9c0d-5a1b2c3d4e5f",
  "osId": "8d3b7c1e-2f4a-4e6b-9c0d-5a1b2c3d4e5f",
  "occurredAt": "2026-11-15T15:25:01Z",
  "payload": {
    "tarefaId": "c0a8012e-0006-4000-8000-000000000006",
    "reservaId": "c0a8012e-0002-4000-8000-000000000002",
    "canceladaEm": "2026-11-15T15:25:01Z"
  }
}
```

### `PagamentoEstornado`

Billing Service → OS Service, após o estorno no gateway (passo 8b). O orquestrador segue com
`LiberarReservaPecas`.

| Campo | Tipo | Obrigatório | Descrição |
|---|---|---|---|
| `cobrancaId` | UUID | sim | Cobrança estornada. |
| `estornoIdExterno` | string | sim | Id do estorno no gateway. |
| `valorEstornado` | decimal | sim | Valor devolvido. |
| `estornadoEm` | ISO-8601 | sim | Estorno. |

```json
{
  "messageId": "5e7d0a10-0000-4000-8000-000000000020",
  "type": "PagamentoEstornado",
  "sagaId": "8d3b7c1e-2f4a-4e6b-9c0d-5a1b2c3d4e5f",
  "osId": "8d3b7c1e-2f4a-4e6b-9c0d-5a1b2c3d4e5f",
  "occurredAt": "2026-11-15T15:20:10Z",
  "payload": {
    "cobrancaId": "b1110000-0005-4000-8000-000000000005",
    "estornoIdExterno": "55512345678",
    "valorEstornado": 590.50,
    "estornadoEm": "2026-11-15T15:20:10Z"
  }
}
```

### `ReservaPecasLiberada`

Execution Service → OS Service, em resposta a `LiberarReservaPecas` (passos 5b, 7b, 8b). Conclui a compensação:
a OS vai para `CANCELADA`.

| Campo | Tipo | Obrigatório | Descrição |
|---|---|---|---|
| `reservaId` | UUID | sim | Reserva liberada. |
| `itensLiberados[]` | lista | sim (pode ser vazia) | `pecaId` + `quantidade` devolvidos ao estoque; vazia se já estava liberada. |
| `liberadaEm` | ISO-8601 | sim | Liberação. |

```json
{
  "messageId": "5e7d0a10-0000-4000-8000-000000000017",
  "type": "ReservaPecasLiberada",
  "sagaId": "8d3b7c1e-2f4a-4e6b-9c0d-5a1b2c3d4e5f",
  "osId": "8d3b7c1e-2f4a-4e6b-9c0d-5a1b2c3d4e5f",
  "occurredAt": "2026-11-15T15:00:03Z",
  "payload": {
    "reservaId": "c0a8012e-0002-4000-8000-000000000002",
    "itensLiberados": [
      {
        "pecaId": "c0a8012e-0003-4000-8000-000000000003",
        "quantidade": 2
      }
    ],
    "liberadaEm": "2026-11-15T15:00:03Z"
  }
}
```

### `ExecucaoFinalizada`

Execution Service → OS Service, quando o reparo termina (passo 9). A OS vai para `FINALIZADA` e a saga para
`CONCLUIDA`.

| Campo | Tipo | Obrigatório | Descrição |
|---|---|---|---|
| `tarefaId` | UUID | sim | Tarefa de reparo. |
| `finalizadaEm` | ISO-8601 | sim | Fim do reparo. |

```json
{
  "messageId": "5e7d0a10-0000-4000-8000-000000000015",
  "type": "ExecucaoFinalizada",
  "sagaId": "8d3b7c1e-2f4a-4e6b-9c0d-5a1b2c3d4e5f",
  "osId": "8d3b7c1e-2f4a-4e6b-9c0d-5a1b2c3d4e5f",
  "occurredAt": "2026-11-15T17:00:00Z",
  "payload": {
    "tarefaId": "c0a8012e-0006-4000-8000-000000000006",
    "finalizadaEm": "2026-11-15T17:00:00Z"
  }
}
```

### `StatusOrdemServicoAlterado`

OS Service → quem assinar (hoje o próprio OS Service, que notifica o cliente; ADR 0010). Publicado via outbox a
cada transição de status da OS (não na abertura), com qualquer origem. **Já implementado (Fase 2).**

| Campo | Tipo | Obrigatório | Descrição |
|---|---|---|---|
| `statusAnterior` | enum | sim | Status da OS antes da transição. |
| `statusNovo` | enum | sim | `RECEBIDA`, `EM_DIAGNOSTICO`, `AGUARDANDO_APROVACAO`, `AGUARDANDO_PAGAMENTO`, `EM_EXECUCAO`, `FINALIZADA`, `ENTREGUE` ou `CANCELADA`. |
| `origem` | enum | sim | `USUARIO`, `SAGA` ou `SISTEMA`. |
| `motivo` | string | não | Motivo registrado no histórico (até 255 caracteres). |
| `alteradoEm` | ISO-8601 | sim | Momento da transição (= `occurredAt`). |

```json
{
  "messageId": "5e7d0a10-0000-4000-8000-000000000021",
  "type": "StatusOrdemServicoAlterado",
  "sagaId": "8d3b7c1e-2f4a-4e6b-9c0d-5a1b2c3d4e5f",
  "osId": "8d3b7c1e-2f4a-4e6b-9c0d-5a1b2c3d4e5f",
  "occurredAt": "2026-11-15T13:10:00.123Z",
  "payload": {
    "statusAnterior": "RECEBIDA",
    "statusNovo": "EM_DIAGNOSTICO",
    "origem": "SAGA",
    "motivo": "DiagnosticoIniciado",
    "alteradoEm": "2026-11-15T13:10:00.123Z"
  }
}
```
