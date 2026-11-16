# Diagrama de Sequência — Abertura de Ordem de Serviço

Fluxo de `POST /ordens-servico`, a partir de um cliente já autenticado (ver
`documentacao/diagrama_sequencia_auth.md`). O OS Service recebe somente cliente
e veículo; o diagnóstico, catálogo e reserva de estoque pertencem ao Execution
Service. A publicação do comando `SolicitarDiagnostico` e o consumo de
`DiagnosticoConcluido` serão conectados na Fase 5, quando a Saga for ativada.

```mermaid
sequenceDiagram
    actor Cliente
    participant GW as API Gateway
    participant Ctrl as OrdemServicoController
    participant Svc as OrdemServicoService
    participant ClienteRepo as ClienteRepository
    participant VeiculoRepo as VeiculoRepository
    participant OSRepo as OrdemServicoRepository
    participant Metrics as MeterRegistry
    participant DB as PostgreSQL

    Cliente ->> GW: POST /ordens-servico\nAuthorization: Bearer <token>\n{clienteId, veiculoId}
    GW ->> Ctrl: proxy (JwtAuthFilter já autenticou)
    Ctrl ->> Svc: abrir(AbrirOrdemServicoCommand)

    Svc ->> ClienteRepo: findById(clienteId)
    ClienteRepo ->> DB: SELECT ...
    DB -->> ClienteRepo: cliente
    ClienteRepo -->> Svc: cliente

    Svc ->> VeiculoRepo: findById(veiculoId)
    VeiculoRepo ->> DB: SELECT ...
    DB -->> VeiculoRepo: veiculo
    VeiculoRepo -->> Svc: veiculo

    Svc ->> Svc: new OrdemServico(cliente, veiculo)\nstatus=RECEBIDA, statusDesde=agora
    Svc ->> OSRepo: save(os)
    OSRepo ->> DB: INSERT INTO ordem_servico ...

    Svc ->> Metrics: counter("oficina.ordens_servico.abertas").increment()
    Svc -->> Ctrl: osSalva
    Ctrl -->> GW: 201 OrdemServicoResponse
    GW -->> Cliente: 201
```

## Pontos de decisão relevantes

- **Sem acesso ao banco alheio**: o OS Service não consulta nem atualiza o
  catálogo do Execution Service. A Saga publicará `SolicitarDiagnostico` após a
  abertura; o snapshot de diagnóstico será salvo quando receber o evento.
- **`statusDesde` != `atualizadoEm`**: `statusDesde` só muda em transições de
  status (`avancarStatus`, `responderOrcamento`, `atualizarStatusExterno`); é
  o campo usado para calcular a métrica
  `oficina_ordens_servico_tempo_no_status_seconds` (ver
  `documentacao/infraestrutura.md`, seção de observabilidade).
- **Métrica de negócio no mesmo fluxo transacional**: o incremento do counter
  `oficina.ordens_servico.abertas` acontece antes do `save` final, mas não
  depende dele — Micrometer não participa da transação JPA (se o `save` final
  falhar e o rollback ocorrer, o contador já incrementado não é desfeito; é
  uma imprecisão aceita, não uma garantia de exatidão contábil).
