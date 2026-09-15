# Diagrama de Sequência — Abertura de Ordem de Serviço

Fluxo de `POST /ordens-servico`, a partir de um cliente já autenticado (ver
`documentacao/diagrama_sequencia_auth.md`). Cobre o caminho feliz com um item
do tipo `PECA` (que reduz estoque) e a métrica de negócio registrada na
abertura.

```mermaid
sequenceDiagram
    actor Cliente
    participant GW as API Gateway
    participant Ctrl as OrdemServicoController
    participant Svc as OrdemServicoService
    participant ClienteRepo as ClienteRepository
    participant VeiculoRepo as VeiculoRepository
    participant PecaRepo as PecaRepository
    participant OSRepo as OrdemServicoRepository
    participant Metrics as MeterRegistry
    participant DB as PostgreSQL

    Cliente ->> GW: POST /ordens-servico\nAuthorization: Bearer <token>\n{clienteId, veiculoId, itens: [...]}
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

    loop para cada item do comando
        alt item do tipo PECA
            Svc ->> PecaRepo: findById(pecaId)
            PecaRepo -->> Svc: peca
            Svc ->> Svc: peca.reduzirEstoque(quantidade)
            Svc ->> PecaRepo: save(peca)
        end
        Svc ->> Svc: os.adicionarItem(item)\n(recalcula valorTotal)
    end

    Svc ->> Metrics: counter("oficina.ordens_servico.abertas").increment()
    Svc ->> OSRepo: save(os)
    OSRepo ->> DB: UPDATE ordem_servico ...\nINSERT INTO item_ordem_servico ...
    OSRepo -->> Svc: osSalva
    Svc -->> Ctrl: osSalva
    Ctrl -->> GW: 201 OrdemServicoResponse
    GW -->> Cliente: 201
```

## Pontos de decisão relevantes

- **Estoque é debitado na abertura, não depois**: `PecaRepository.save` roda
  dentro da mesma transação de `abrir()` — se qualquer passo falhar depois, a
  transação inteira (incluindo a redução de estoque) é revertida.
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
