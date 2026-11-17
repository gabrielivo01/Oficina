# Arquitetura da Fase 4

## Visão geral

```mermaid
flowchart LR
    Cliente -->|REST / JWT| Ingress
    Mecanico -->|REST / JWT| Ingress
    Ingress --> OS[OS Service]
    Ingress --> EX[Execution Service]
    Ingress --> BI[Billing Service]
    OS --> OSDB[(PostgreSQL OS)]
    EX --> EXDB[(PostgreSQL Execution)]
    BI --> BDB[(MongoDB replica set rs0)]
    OS <-->|oficina.comandos / oficina.eventos| RMQ[RabbitMQ]
    EX <-->|execution.comandos / execution.*| RMQ
    BI <-->|billing.comandos / billing.*| RMQ
    BI -->|Checkout Pro / fake| MP[Gateway de pagamento]
    BI -->|outbox de e-mail| SMTP[SMTP]
    OS -->|OTLP| OTel[OpenTelemetry Collector]
    EX -->|OTLP| OTel
    BI -->|OTLP| OTel
    OTel --> Jaeger
    OS -. /actuator/prometheus .-> Prometheus
    EX -. /actuator/prometheus .-> Prometheus
    BI -. /actuator/prometheus .-> Prometheus
    RMQ -. plugin Prometheus .-> Prometheus
    Prometheus --> Grafana
```

Cada serviço é dono dos seus dados. As mensagens seguem contratos versionados, são gravadas pelo
Transactional Outbox e processadas com inbox/idempotência. O OS Service é o orquestrador e a fonte
oficial do status da ordem de serviço. Decisões: [ADR 0004](./adrs/0004-divisao-em-microsservicos.md),
[ADR 0005](./adrs/0005-saga-orquestrada.md), [ADR 0006](./adrs/0006-rabbitmq-como-mensageria.md),
[ADR 0007](./adrs/0007-mongodb-no-billing.md) e [ADR 0009](./adrs/0009-outbox-e-idempotencia.md).
Visão completa, com filas, DLQs, réplicas, observabilidade e pipeline de entrega:
[arquitetura_geral.mermaid](./arquitetura_geral.mermaid).

## Sequência do caminho feliz

```mermaid
sequenceDiagram
    autonumber
    actor Cliente
    participant OS as OS Service (orquestrador)
    participant EX as Execution Service
    participant BI as Billing Service
    participant MP as Gateway
    Cliente->>OS: Abre OS
    OS->>OS: Persiste OS, Saga e SolicitarDiagnostico na outbox
    OS-->>EX: SolicitarDiagnostico
    EX-->>OS: DiagnosticoIniciado
    actor Mecanico
    Mecanico->>EX: Conclui diagnóstico
    EX->>EX: Reserva peças e persiste snapshot + outbox
    EX-->>OS: DiagnosticoConcluido
    OS->>OS: Atualiza snapshot da OS e grava GerarOrcamento
    OS-->>BI: GerarOrcamento
    BI-->>OS: OrcamentoGerado
    Cliente->>BI: Aprova orçamento
    BI-->>OS: OrcamentoAprovado
    OS-->>BI: CriarCobranca
    BI->>MP: Cria preferência de pagamento
    BI-->>OS: CobrancaCriada
    Cliente->>MP: Paga
    BI->>MP: Confirma pagamento (webhook verificado ou consulta)
    BI-->>OS: PagamentoConfirmado
    OS-->>EX: IniciarExecucao
    EX-->>OS: ExecucaoIniciada
    Mecanico->>EX: Finaliza reparo
    EX-->>OS: ExecucaoFinalizada
    OS->>OS: Marca OS FINALIZADA e publica evento de status
    Cliente->>OS: Registra entrega
    OS->>OS: Marca OS ENTREGUE
```

## Compensações

```mermaid
sequenceDiagram
    participant OS as OS Service (orquestrador)
    participant EX as Execution Service
    participant BI as Billing Service
    Note over OS,EX: Orçamento recusado ou pagamento rejeitado/expirado
    OS->>EX: LiberarReservaPecas
    EX-->>OS: ReservaPecasLiberada
    OS->>OS: Marca OS CANCELADA
    Note over OS,BI: Expiração do pagamento
    OS->>BI: CancelarCobranca
    BI-->>OS: CobrancaCancelada
    OS->>EX: LiberarReservaPecas
    EX-->>OS: ReservaPecasLiberada
    Note over OS,EX: Recusa da execução após pagamento
    OS->>BI: EstornarPagamento
    BI-->>OS: PagamentoEstornado
    OS->>EX: LiberarReservaPecas
    EX-->>OS: ReservaPecasLiberada
    OS->>OS: Marca OS CANCELADA
    Note over OS,EX: Cancelamento manual durante execução
    OS->>EX: CancelarExecucao
    EX-->>OS: ExecucaoCancelada
    OS->>BI: EstornarPagamento
    BI-->>OS: PagamentoEstornado
    OS->>EX: LiberarReservaPecas
    EX-->>OS: ReservaPecasLiberada
    OS->>OS: Marca OS CANCELADA
```

Os contratos completos, payloads, filas, roteamento e fixtures ficam em
[contratos_mensagens.md](./contratos_mensagens.md). Os diagramas descrevem o fluxo implementado; uma
captura de trace distribuído e a validação do fluxo real dependem de executar a plataforma.
