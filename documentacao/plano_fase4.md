# Plano de Implementação — Fase 4: Microsserviços + Saga

> **Como usar este plano (para o Claude Code):** execute **uma fase por sessão**, na ordem.
> Antes de codar, apresente o plano da fase em modo de plano e espere aprovação.
> Ao final de cada **Etapa**, rode a verificação (`./mvnw -q verify`), pare e proponha a mensagem de commit.
> Não antecipe trabalho de fases futuras. Se algo deste plano conflitar com o código real, **pare e pergunte**: não improvise.

---

## 1. Decisões já tomadas (não reabrir sem pedir)

| Tema | Decisão |
|---|---|
| Serviços | 3 microsserviços: **OS Service** (repo `oficina`, evoluído), **Execution Service** (novo repo `oficina-execution-service`), **Billing Service** (novo repo `oficina-billing-service`) |
| Saga | **Orquestrada**, orquestrador dentro do OS Service, estado persistido no banco do OS Service |
| Mensageria | **RabbitMQ** (Spring AMQP), rodando no cluster |
| Bancos | OS Service → **PostgreSQL**; Execution Service → **PostgreSQL** (instância própria); Billing Service → **MongoDB** (NoSQL) |
| Pagamento | **Mercado Pago (sandbox)**, cobrado **após a aprovação do orçamento** e **antes da execução** |
| Ambiente | **100% local, custo zero**: Docker Compose para desenvolvimento, **kind** para Kubernetes (local e no CI). Sem AWS nesta fase |
| Rastreamento | **OpenTelemetry → OTel Collector → Jaeger**, mais o Prometheus/Grafana da Fase 3 |
| Qualidade | **JaCoCo ≥ 80%** por serviço + **SonarCloud** com Quality Gate no CI |
| BDD | **Cucumber** no OS Service, cobrindo o fluxo completo da Saga (caminho feliz + compensações) |
| Fora do escopo | Lambda de CPF, API Gateway e Terraform AWS da Fase 3 ficam **como estão**. Não apagar, não migrar. O endpoint `/internal/clientes/{cpf}/status` e a claim `tipo` do JWT **não podem mudar de contrato** |

### Divisão de responsabilidades

| Serviço | Dono de | Não é dono de |
|---|---|---|
| **OS Service** (Postgres) | Cliente, Veículo, Usuário/Auth (JWT), Ordem de Serviço, **histórico de status**, **orquestrador da Saga** | Peças/estoque, orçamento, pagamento |
| **Execution Service** (Postgres) | **Catálogo de peças e estoque** (com reserva), **fila de execução** (tarefas de diagnóstico e reparo), progresso do diagnóstico/reparo | Preço final, pagamento, status oficial da OS |
| **Billing Service** (MongoDB) | **Orçamento** (snapshot dos itens), aprovação/recusa, **cobrança e pagamento** (Mercado Pago), estorno | Estoque, execução |

**Por que o estoque vai para o Execution Service:** é ele que diagnostica e consome as peças. A reserva e a liberação viram passos reais da Saga, com compensação entre serviços, e o OS Service fica focado em OS e orquestração.

---

## 2. Regras globais (valem para todas as fases e todos os repositórios)

1. **Padrão de arquitetura:** o mesmo do `oficina` (presentation → application → domain; infrastructure implementa portas). Cada repo novo nasce com `CLAUDE.md`, ArchUnit, Spotless, JaCoCo e Flyway (ou índices Mongo versionados), copiados e adaptados do `oficina`.
2. **Nenhum serviço acessa o banco de outro.** Comunicação só por mensagens (RabbitMQ) ou REST síncrono quando estritamente necessário (consulta).
3. **Transactional Outbox em todo serviço que publica mensagens:** a mudança de estado e a mensagem a publicar são gravadas na **mesma transação** (tabela/coleção `outbox`); um publicador agendado envia para o RabbitMQ e marca como enviado. Nunca publicar direto de dentro de `@Transactional`.
4. **Consumidores idempotentes (Inbox):** toda mensagem tem `messageId`; o consumidor registra os IDs processados (tabela/coleção `inbox` com chave única) e ignora duplicatas.
5. **Envelope padrão de mensagem** (JSON):
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
   O contexto de trace (W3C `traceparent`) vai nos headers AMQP, propagado automaticamente pelo Micrometer/OTel.
6. **Sem biblioteca compartilhada entre repositórios.** Cada serviço define os próprios DTOs de mensagem. O contrato oficial fica em `documentacao/contratos_mensagens.md` no repo `oficina`, e cada consumidor tem um **teste de contrato** que desserializa o JSON de exemplo do catálogo.
7. **Configuração tipada** com `@ConfigurationProperties`; segredos só por variável de ambiente; **segurança ligada por padrão** em todos os serviços.
8. **Testes:** domínio = unitário puro; application = mocks; presentation = `@WebMvcTest`; integração com **Testcontainers** (Postgres, MongoDB, RabbitMQ). Bug = teste que falha antes da correção.
9. **Cobertura ≥ 80%** (linha) por serviço, medida pelo JaCoCo. Exclusões permitidas: `*Application`, `config/**`, records de Request/Response. Nada de excluir domínio ou application para bater a meta.
10. **Documentação viva:** toda decisão nova vira ADR em `documentacao/adrs/` (formato dos ADRs existentes). Swagger (springdoc) em todos os serviços.
11. **JWT:** os três serviços validam o mesmo token HS256 (segredo compartilhado `JWT_SECRET`). Só o OS Service **emite** tokens de staff. Use jjwt 0.12.x nos serviços novos e atualize o OS Service para a mesma versão (Fase 2).

---

## 3. Máquina de estados e Saga

### 3.1 Status da OS (visão oficial, no OS Service)

```
RECEBIDA → EM_DIAGNOSTICO → AGUARDANDO_APROVACAO → AGUARDANDO_PAGAMENTO → EM_EXECUCAO → FINALIZADA → ENTREGUE
                  \__________________\______________________\________________\→ CANCELADA
```
- `CANCELADA` é alcançável de qualquer status **antes** de `FINALIZADA`, e só como resultado de compensação concluída ou de cancelamento explícito (que também dispara compensação).
- Toda transição grava uma linha em `historico_status_os` (status anterior, novo status, data/hora, origem: `USUARIO` | `SAGA` | `SISTEMA`, motivo).

### 3.2 Fluxo da Saga (orquestrador no OS Service)

| # | Gatilho (evento recebido) | Comando enviado pelo orquestrador | Serviço destino | Status da OS após o evento |
|---|---|---|---|---|
| 1 | OS aberta (local) | `SolicitarDiagnostico` | Execution | RECEBIDA |
| 2 | `DiagnosticoIniciado` | — | — | EM_DIAGNOSTICO |
| 3 | `DiagnosticoConcluido` (itens + preços, estoque **reservado**) | `GerarOrcamento` | Billing | EM_DIAGNOSTICO |
| 4 | `OrcamentoGerado` | — (Billing notifica o cliente) | — | AGUARDANDO_APROVACAO |
| 5a | `OrcamentoAprovado` | `CriarCobranca` | Billing | AGUARDANDO_APROVACAO |
| 5b | `OrcamentoRecusado` | **compensação:** `LiberarReservaPecas` | Execution | → CANCELADA após `ReservaPecasLiberada` |
| 6 | `CobrancaCriada` (link/QR do Mercado Pago) | — | — | AGUARDANDO_PAGAMENTO |
| 7a | `PagamentoConfirmado` | `IniciarExecucao` | Execution | AGUARDANDO_PAGAMENTO |
| 7b | `PagamentoRejeitado` **ou** prazo de pagamento expirado | **compensação:** `CancelarCobranca` (se pendente) → `LiberarReservaPecas` | Billing → Execution | → CANCELADA |
| 8a | `ExecucaoIniciada` (estoque reservado vira **baixa definitiva**) | — | — | EM_EXECUCAO |
| 8b | `ExecucaoRecusada` | **compensação:** `EstornarPagamento` → (após `PagamentoEstornado`) `LiberarReservaPecas` | Billing → Execution | → CANCELADA |
| 9 | `ExecucaoFinalizada` | — (Saga **CONCLUIDA**) | — | FINALIZADA |
| 10 | Entrega ao cliente (ação manual no OS Service, fora da Saga) | — | — | ENTREGUE |

**Estado da Saga** (tabela `saga_ordem_servico` no OS Service): `sagaId`, `osId`, `etapaAtual`, `situacao` (`EM_ANDAMENTO` | `COMPENSANDO` | `CONCLUIDA` | `COMPENSADA` | `FALHA_COMPENSACAO`), `prazoExpiracao`, `tentativas`, `@Version`, timestamps.

**Regras do orquestrador:**
- Implementado como **máquina de estados pura no domínio** (`SagaOrdemServico.aoReceber(evento) → List<Comando>`), testável sem Spring. A camada application só carrega/salva a saga e grava os comandos no outbox.
- **Timeouts:** um job agendado procura sagas com `prazoExpiracao` vencido (ex.: pagamento não confirmado em 30 min, configurável) e dispara a compensação correspondente.
- **Compensações são idempotentes e reexecutáveis.** Se uma compensação falhar após N tentativas, a saga vai para `FALHA_COMPENSACAO`, gera log de erro, métrica e alerta, e fica visível no endpoint de consulta da saga.
- **Injeção de falha para a demonstração:** no Execution Service, a propriedade `app.simulacao.recusar-inicio-execucao` (padrão `false`), ou uma placa específica configurável, faz o Execution responder `ExecucaoRecusada`. Isso demonstra o estorno no vídeo. Desligado por padrão e documentado.

### 3.3 Topologia RabbitMQ

- Exchange `oficina.comandos` (direct): uma fila de comandos por serviço: `execution.comandos`, `billing.comandos`.
- Exchange `oficina.eventos` (topic): o OS Service consome em `os.saga.eventos` (binding `#`, ou só os tipos da saga).
- Cada fila com **DLQ** (`*.dlq`) e retry com backoff (3 tentativas) antes de ir para a DLQ.
- Mensagens persistentes; filas duráveis; publisher confirms ligados.

---

## 4. Bugs e dívidas da análise: onde cada um é resolvido

| Item da análise | Resolvido em |
|---|---|
| `recusarOrcamento()` sem efeito (OS presa) | Fase 5: recusa vira compensação → `CANCELADA` |
| `cancelar()` não muda o status | Fase 2: status `CANCELADA` + histórico; Fase 5: o cancelamento dispara a compensação |
| `removerItem()` em OS finalizada | Fase 3: itens passam a pertencer ao diagnóstico no Execution, e ficam **imutáveis após `DiagnosticoConcluido`** |
| Corrida no estoque (`Peca` sem `@Version`) | Fase 3: `@Version` + reserva atômica no Execution |
| Notificação enviada antes do commit | Fase 2: outbox; a notificação vira efeito pós-commit |
| `StatusNotificationPort` em infrastructure usado por application | Fase 2: mover a porta para application (ou domain); corrigir a violação congelada no ArchUnit |
| Segurança desligada por padrão | Fase 2 (OS) e nascimento dos novos serviços |
| `ddl-auto=update` | Fase 2: Flyway no OS Service com baseline; serviços novos já nascem com Flyway; `ddl-auto=validate` |
| `listarTodas()` com `findAll()` em memória | Fase 2: query com filtro de status + paginação |
| Lógica duplicada `adicionarItem`/`adicionarItemInterno` | Fase 3: o código sai do OS Service |
| Chave interna comparada com `equals` | Fase 2: `MessageDigest.isEqual` |
| jjwt 0.11.5 | Fase 2: atualizar para 0.12.x |
| Terraform sem backend, EKS público, bypass do API Gateway | **Fora do escopo** (sem AWS nesta fase). Registrar como dívida no README |

---

## 5. Pré-requisito — Fase 0: concluir a padronização

Concluir as **Partes 2 a 5** do prompt de padronização no repo `oficina` (ArchUnit com FreezingArchRule, Spotless + JaCoCo + `verify` no CI, hooks, skill `novo-caso-de-uso` e subagent `revisor`). Os repos novos vão **copiar** esse ferramental, então ele precisa estar pronto e estável antes.

Além disso, criar estes ADRs (só documentação, sem código):
- `0004-divisao-em-microsservicos.md`: os 3 serviços, as responsabilidades (seção 1) e por que o estoque fica no Execution.
- `0005-saga-orquestrada.md`: orquestração vs. coreografia; por que orquestração (fluxo e compensações explícitos num lugar só, mais fáceis de testar e rastrear).
- `0006-rabbitmq-como-mensageria.md`: atualiza o ADR 0001 (REST síncrono continua para consultas; eventos e comandos via RabbitMQ). Marcar o ADR 0001 como "Parcialmente substituído por 0006".
- `0007-mongodb-no-billing.md`: o orçamento é um documento (snapshot imutável dos itens), e o requisito de NoSQL é atendido.
- `0008-ambiente-local-kind.md`: custo zero; kind no CI cumpre o requisito de "deploy automatizado em Kubernetes"; o caminho AWS fica como evolução.
- `0009-outbox-e-idempotencia.md`.

**Commit:** `docs: ADRs da fase 4 (microsservicos, saga, mensageria, nosql, ambiente)`

---

## 6. Fase 1 — Plataforma local (repo `oficina-infra-k8s`)

**Objetivo:** subir com um comando tudo o que os serviços precisam para rodar e ser observados, no Docker Compose (dev) e no kind (K8s).

### Etapa 1.1 — Docker Compose de plataforma
- `local/docker-compose.yml` com: RabbitMQ (com management UI), Postgres do OS (porta 5432), Postgres do Execution (porta 5433), MongoDB (27017), Jaeger (UI 16686), OTel Collector (OTLP 4317/4318), MailHog.
- `local/otel-collector-config.yaml`: recebe OTLP e exporta para o Jaeger.
- `local/rabbitmq/definitions.json`: exchanges, filas, DLQs e bindings da seção 3.3 (versionados, carregados no boot).
- **Aceite:** `docker compose up -d` sobe tudo saudável; a UI do RabbitMQ mostra as filas; o Jaeger abre.

### Etapa 1.2 — Cluster kind
- `kind/cluster.yaml` (1 control-plane + 1 worker, com portas mapeadas para o ingress).
- `scripts/plataforma_up.sh` / `plataforma_down.sh`: criam/destroem o cluster; instalam ingress-nginx, RabbitMQ (manifest ou chart, com as mesmas definitions), Jaeger + OTel Collector, e reaproveitam o `scripts/install_observability_stack.sh` existente (kube-prometheus-stack).
- O mesmo script deve funcionar **no GitHub Actions** (vai ser chamado pelos pipelines dos serviços na Fase 7).
- **Aceite:** `scripts/plataforma_up.sh` deixa o cluster pronto; `kubectl get pods -A` tudo `Running`; Grafana, Jaeger e RabbitMQ acessíveis via port-forward ou ingress.

### Etapa 1.3 — README da plataforma
- Pré-requisitos (Docker, kind, kubectl, helm), comandos, portas, credenciais de dev, troubleshooting.

**Commits:** um por etapa.

---

## 7. Fase 2 — Preparar o OS Service (repo `oficina`)

**Objetivo:** deixar o OS Service pronto para virar orquestrador: status novos, histórico, mensageria confiável, tracing e as dívidas resolvidas. **Ainda sem a Saga.**

### Etapa 2.1 — Flyway
- Adicionar o Flyway; gerar `V1__baseline.sql` a partir do schema atual (Postgres); `ddl-auto=validate` em todos os perfis; H2 só nos testes que já o usam (preferir Testcontainers Postgres nos testes de integração novos).
- **Aceite:** a app sobe do zero só com as migrations; testes passam.

### Etapa 2.2 — Status, histórico e cancelamento
- Novos status `AGUARDANDO_PAGAMENTO` e `CANCELADA` no enum, com as transições da seção 3.1.
- Entidade/tabela `historico_status_os`; **toda** mudança de status passa por um único método do agregado, que registra o histórico.
- `cancelar()` passa a transicionar para `CANCELADA` (a compensação real vem na Fase 5).
- Endpoint `GET /ordens-servico/{id}/historico`.
- `listarTodas()`: query com filtro de status + paginação.
- **Testes primeiro** para as transições válidas e inválidas e para o histórico.

### Etapa 2.3 — Segurança e dívidas pontuais
- `app.security.enabled` com padrão `true`; perfil `local` desliga explicitamente.
- `InternalApiKeyFilter` com `MessageDigest.isEqual`.
- jjwt 0.11.5 → 0.12.x.
- Mover `StatusNotificationPort` para fora de infrastructure e remover a violação do store do ArchUnit.

### Etapa 2.4 — Infra de mensageria (outbox + inbox)
- Spring AMQP; `@ConfigurationProperties` com exchanges e filas.
- Tabela `outbox` (id, tipo, routingKey, exchange, payload JSON, criadoEm, enviadoEm, tentativas) + publicador agendado (publisher confirms; marca como enviado só após o ack).
- Tabela `inbox` (messageId PK, processadoEm) + componente de idempotência usado pelos listeners.
- A notificação por e-mail de mudança de status passa a ser disparada **após o commit**.
- Testes de integração com Testcontainers (Postgres + RabbitMQ): gravar no outbox → mensagem chega à fila; mensagem duplicada → processada uma vez.

### Etapa 2.5 — Tracing distribuído
- Micrometer Tracing + exportador OTLP (verifique a forma recomendada no Spring Boot 4; se existir starter oficial de OpenTelemetry, use-o) apontando para o OTel Collector.
- Observação habilitada no RabbitTemplate e nos listeners, para propagar o `traceparent`.
- `traceId` e `spanId` nos logs JSON (já existe correlation ID: integrar, não duplicar).
- **Aceite:** uma requisição HTTP que publica uma mensagem aparece no Jaeger como um único trace.

### Etapa 2.6 — Catálogo de contratos
- `documentacao/contratos_mensagens.md`: todos os comandos e eventos da seção 3.2, com envelope, payload, produtor, consumidor, fila e exemplo JSON completo de cada um.
- Os exemplos JSON também ficam em `src/test/resources/contratos/*.json` (usados pelos testes de contrato).

**Commits:** um por etapa. **Verificação:** `./mvnw -q verify` verde, cobertura não cai.

---

## 8. Fase 3 — Execution Service (novo repo `oficina-execution-service`)

> Antes de começar: o usuário cria o repositório no GitHub (`gh repo create gabrielivo01/oficina-execution-service --public`) e o clona como irmão de `oficina`.

### Etapa 3.1 — Esqueleto padronizado
- Spring Boot 4.0.x, Java 21, Maven Wrapper, mesmo pacote base `io.github.gabrielivo.oficina.execution`.
- Copiar e adaptar do `oficina`: `CLAUDE.md`, ArchUnit (regras completas, **sem freeze**, já que o código é novo), Spotless, JaCoCo 80%, Flyway, security (validação de JWT), springdoc, actuator + Prometheus, tracing OTLP, logs JSON, outbox/inbox, Dockerfile, `.githooks`, `.claude/` (hooks, skill, revisor).
- **Aceite:** `./mvnw verify` verde com um teste de arquitetura e um health check.

### Etapa 3.2 — Peças e estoque (migrado do OS Service)
- Migrar `Peca` e as regras de estoque (reduzir, repor, estoque mínimo, alertas) para este serviço, com **`@Version`**.
- Novo conceito de **reserva**: `ReservaPecas` por OS (itens + quantidades, status `RESERVADA` | `CONSUMIDA` | `LIBERADA`). Reservar diminui o disponível; liberar devolve; consumir dá baixa definitiva. Operações idempotentes por `osId`.
- CRUD de peças + reposição + alertas (os mesmos endpoints que existem hoje no OS Service, com os mesmos contratos de Request/Response sempre que possível).
- Migration com seed de peças de exemplo (substitui o `DataSeeder` de peças).

### Etapa 3.3 — Fila de execução
- Agregado `TarefaExecucao` (osId, tipo `DIAGNOSTICO` | `REPARO`, status `NA_FILA` → `EM_ANDAMENTO` → `CONCLUIDA` | `RECUSADA`, prioridade, timestamps).
- Endpoints para o mecânico: listar a fila (ordenada por prioridade e chegada), iniciar diagnóstico, **concluir o diagnóstico informando os itens** (serviços com valor; peças por `pecaId` + quantidade, com o preço vindo do catálogo), iniciar reparo, registrar progresso, finalizar reparo.
- Concluir o diagnóstico **reserva o estoque na mesma transação**; se faltar estoque, responde 422 ao mecânico (não é evento de Saga). Após concluído, os itens ficam **imutáveis**.

### Etapa 3.4 — Participação na Saga (comandos e eventos)
- Consome em `execution.comandos`: `SolicitarDiagnostico` (cria a tarefa de diagnóstico na fila), `IniciarExecucao` (consome a reserva, cria a tarefa de reparo; respeita a injeção de falha `app.simulacao.*` → `ExecucaoRecusada`), `LiberarReservaPecas`.
- Publica (via outbox): `DiagnosticoIniciado`, `DiagnosticoConcluido`, `ExecucaoIniciada`, `ExecucaoRecusada`, `ExecucaoFinalizada`, `ReservaPecasLiberada`.
- Testes de contrato contra os JSONs do catálogo; testes de integração com Testcontainers (Postgres + RabbitMQ).

### Etapa 3.5 — Manifests K8s e README
- `k8s/` com Kustomize (base + overlay `local`): Deployment, Service, ConfigMap, Secret de dev, HPA, Postgres próprio (StatefulSet + PVC), ServiceMonitor, regra no ingress.
- README: responsabilidades, endpoints, mensagens, como rodar, arquitetura do serviço (diagrama Mermaid).

### Etapa 3.6 — Remover peças/estoque do OS Service
- No `oficina`: remover `Peca`, `ItemPeca`, `PecaService`, `PecaController` e a lógica de estoque de `OrdemServicoService`; migration Flyway removendo as tabelas; ajustar os testes. Os itens da OS no OS Service passam a ser um **snapshot somente leitura**, recebido em `DiagnosticoConcluido` (para exibir na consulta da OS).
- **Atenção:** esta etapa quebra o fluxo antigo de abertura de OS com itens. Isso é esperado; o fluxo novo é concluído na Fase 5. Manter a branch funcional (testes verdes) mesmo com o fluxo incompleto.

**Commits:** um por etapa, em cada repo.

---

## 9. Fase 4 — Billing Service (novo repo `oficina-billing-service`)

> O usuário cria o repo no GitHub, cria uma **conta de teste no Mercado Pago** (credenciais de sandbox) e guarda `MP_ACCESS_TOKEN` e `MP_WEBHOOK_SECRET` como variáveis de ambiente locais (**nunca** commitar).

### Etapa 4.1 — Esqueleto padronizado com MongoDB
- Mesmo ferramental da Etapa 3.1, trocando JPA/Flyway por **Spring Data MongoDB**. Índices criados de forma versionada (ex.: Mongock, ou `@Indexed` + inicializador explícito; justificar a escolha). Outbox e inbox como **coleções** (inbox com índice único em `messageId`). Para gravar a mudança de estado e o outbox de forma atômica, usar transação Mongo (exige replica set: configurar o Mongo de dev e o de teste como replica set de 1 nó) **ou** gravar o evento dentro do próprio documento e publicar a partir dele. Escolher, justificar num ADR do repo e testar.

### Etapa 4.2 — Orçamento
- Documento `Orcamento` (osId, itens snapshot, valor total, status `PENDENTE` → `APROVADO` | `RECUSADO` | `CANCELADO`, datas, quem respondeu).
- Consome `GerarOrcamento` → cria o orçamento → notifica o cliente (e-mail via MailHog no local) → publica `OrcamentoGerado`.
- Endpoints `GET /orcamentos/{osId}` e `POST /orcamentos/{id}/resposta` (`aprovado: true|false`, observação), aceitando token de staff (o atendente registra a resposta do cliente) **ou** token de cliente (claim `tipo`). Publica `OrcamentoAprovado` ou `OrcamentoRecusado`.
- **Migrar** o domínio `Pagamento` do OS Service para cá (como referência para a Etapa 4.3) e remover do OS Service ao final da fase.

### Etapa 4.3 — Cobrança com gateway plugável
- Porta `GatewayPagamento` (criarCobranca, consultarPagamento, estornar, cancelar) com duas implementações:
  - `GatewayPagamentoFake` (perfis `local` e `test`, e também no CI): aprova ou recusa de forma determinística e configurável, para os testes e para a Saga rodarem sem internet.
  - `GatewayPagamentoMercadoPago` (perfil `mercadopago`): integração real com o sandbox.
- Documento `Cobranca` (osId, orcamentoId, valor, idExterno, status `PENDENTE` → `APROVADA` | `REJEITADA` | `CANCELADA` | `ESTORNADA`, link de pagamento).
- Consome `CriarCobranca` → publica `CobrancaCriada` (com o link). Consome `CancelarCobranca` e `EstornarPagamento` → publica `PagamentoEstornado`.

### Etapa 4.4 — Mercado Pago real
- **Consulte a documentação oficial atual do Mercado Pago antes de implementar** e escolha o produto mais simples que gere um link de pagamento testável no sandbox (provavelmente Checkout Pro / preferência; justificar no README). Usar o SDK Java oficial, se estiver mantido e compatível com Java 21; senão, um cliente HTTP (RestClient).
- Webhook `POST /webhooks/mercadopago`: **validar a assinatura** conforme a documentação, **nunca confiar no corpo**: consultar o pagamento na API pelo id recebido, atualizar a `Cobranca` e publicar `PagamentoConfirmado` ou `PagamentoRejeitado`. Idempotente (o Mercado Pago reenvia notificações).
- **Plano B para ambiente sem URL pública:** um job agendado consulta as cobranças `PENDENTE` na API (intervalo configurável). Documentar o uso do ngrok/Cloudflare Tunnel para receber o webhook localmente na demonstração.
- Estorno real via API para `EstornarPagamento`.
- Testes: o adapter do Mercado Pago testado com WireMock (sem rede); a validação de assinatura com vetores conhecidos.

### Etapa 4.5 — Manifests K8s e README
- Como na Etapa 3.5, com MongoDB próprio (StatefulSet) e o Secret do Mercado Pago **fora do repositório** (criado por script a partir de variáveis de ambiente).

**Commits:** um por etapa.

---

## 10. Fase 5 — Saga orquestrada (repo `oficina`)

### Etapa 5.1 — Máquina de estados da Saga (domínio puro)
- `SagaOrdemServico` com todas as transições e compensações da seção 3.2, inclusive as de timeout e as de falha de compensação.
- **Testes unitários exaustivos** (sem Spring): cada evento em cada estado; eventos fora de ordem ou duplicados são ignorados com segurança; o caminho de compensação de cada falha.

### Etapa 5.2 — Orquestrador na camada application
- Abrir OS → cria a Saga → grava `SolicitarDiagnostico` no outbox (na mesma transação).
- Listener de `os.saga.eventos` (com inbox) → carrega a saga → aplica o evento → atualiza o status da OS e o histórico (origem `SAGA`) → grava os comandos resultantes no outbox. **Tudo numa transação.**
- Job de timeout (prazo de pagamento configurável).
- Cancelamento manual da OS → dispara as compensações conforme a etapa atual.
- Endpoint `GET /ordens-servico/{id}/saga` (etapa, situação, histórico de eventos da saga) para a demonstração.
- Métricas: sagas iniciadas, concluídas, compensadas, em falha de compensação; duração por etapa. Alerta Prometheus para `FALHA_COMPENSACAO`.

### Etapa 5.3 — Limpeza do fluxo antigo
- Remover `Pagamento` do OS Service (já migrado), o `PagamentoController`, `responderOrcamento` e `avancarStatus` manuais que conflitem com a Saga (manter só a transição manual `FINALIZADA → ENTREGUE`). Migration removendo as tabelas. Atualizar o Swagger.
- Garantir que a violação congelada do ArchUnit não voltou e que o store diminuiu.

### Etapa 5.4 — Teste de integração da Saga
- Com Testcontainers (Postgres + RabbitMQ): **participantes simulados** (listeners de teste que respondem como Execution e Billing) para validar o caminho feliz e as três compensações (orçamento recusado, pagamento rejeitado/expirado, execução recusada).

**Commits:** um por etapa.

---

## 11. Fase 6 — BDD, cobertura e qualidade (todos os repos)

### Etapa 6.1 — BDD com Cucumber (repo `oficina`)
- Cucumber + JUnit Platform Suite; features em `src/test/resources/features/`, em português (`# language: pt`).
- `saga_ordem_servico.feature` com cenários:
  1. Fluxo completo: OS aberta → diagnóstico → orçamento aprovado → pagamento confirmado → execução → finalizada → entregue.
  2. Orçamento recusado → reserva liberada → OS cancelada.
  3. Pagamento não confirmado no prazo → cobrança cancelada → reserva liberada → OS cancelada.
  4. Execução recusada → pagamento estornado → reserva liberada → OS cancelada.
- Infra dos steps: a mesma da Etapa 5.4 (Testcontainers + participantes simulados), reaproveitada.
- Relatório HTML do Cucumber publicado como artefato do CI.

### Etapa 6.2 — Cobertura ≥ 80% em cada serviço
- Rodar o JaCoCo nos 3 repos; preencher as lacunas com testes **significativos** (regras e caminhos de erro, não getters). Subir o limite do JaCoCo para 0.80 nos 3 repos.

### Etapa 6.3 — SonarCloud
- Projeto SonarCloud por repo (o usuário cria a organização e os projetos e gera o `SONAR_TOKEN`); `sonar-project.properties` ou configuração no `pom.xml`; relatório do JaCoCo enviado ao Sonar; Quality Gate obrigatório.
- Badges de cobertura e Quality Gate no README de cada repo (são a "evidência de cobertura" pedida nos entregáveis).

**Commits:** um por etapa por repo.

---

## 12. Fase 7 — CI/CD com deploy em Kubernetes (todos os repos)

### Etapa 7.1 — Pipeline padrão por serviço (`.github/workflows/`)
- **`ci.yml`** (pull_request e push): checkout → Java 21 → `./mvnw verify` (testes, ArchUnit, Spotless check, JaCoCo 80%) → análise SonarCloud com Quality Gate → build da imagem Docker.
- **`cd.yml`** (push na `main`, depois do CI verde):
  1. Build e push da imagem para o **GHCR** (`ghcr.io/gabrielivo01/<servico>:<sha>`).
  2. Checkout do `oficina-infra-k8s` → `scripts/plataforma_up.sh` (kind no runner).
  3. Deploy do serviço (Kustomize overlay `local` com a tag da imagem) **e dos outros dois serviços** pela última imagem publicada, para o fluxo funcionar.
  4. **Validação:** `kubectl rollout status`; health checks; **smoke test E2E** (script que abre uma OS via API e acompanha até `FINALIZADA` usando o gateway de pagamento fake e endpoints do mecânico).
  5. Em falha: coletar logs dos pods como artefato.
- Reaproveitar ou adaptar `scripts/ci_deploy.sh` e `render_k8s_overlay.sh` existentes no `oficina`.

### Etapa 7.2 — Proteção de branch
- Script `scripts/proteger_branch_main.sh` (com `gh api`) que aplica em cada repo: PR obrigatório, CI e Quality Gate obrigatórios, sem push direto na `main`, branch atualizada antes do merge. **O usuário executa o script** (exige permissão de admin).

### Etapa 7.3 — Repos de infra
- `oficina-infra-k8s`: workflow que valida os scripts e manifests (shellcheck, `kubectl kustomize`, kubeconform) e roda `plataforma_up.sh` num kind de teste.

**Commits:** um por etapa por repo.

---

## 13. Fase 8 — Observabilidade dos fluxos distribuídos

- Dashboards Grafana: um por serviço (HTTP, JVM, filas) + um **dashboard da Saga** (sagas por situação, duração por etapa, compensações, filas e DLQs do RabbitMQ).
- ServiceMonitor dos 3 serviços + métricas do RabbitMQ (plugin Prometheus).
- PrometheusRules: saga em `FALHA_COMPENSACAO`, mensagens em DLQ, latência p95 alta.
- **Validação manual guiada:** executar o fluxo completo e conferir no Jaeger **um trace único** atravessando OS → Execution → Billing → OS (HTTP + AMQP). Registrar prints na documentação.

---

## 14. Fase 9 — Documentação e entregáveis

### Etapa 9.1 — README de cada repo
- O que o serviço faz; arquitetura (diagrama Mermaid); endpoints (link do Swagger); mensagens produzidas e consumidas; como rodar (local e kind); badges e evidências de cobertura; decisões (links dos ADRs).
- README do `oficina`: seção **"Saga Pattern"** com a escolha (orquestrada), a justificativa, o diagrama de sequência do caminho feliz e das compensações, e a tabela da seção 3.2 (exigido pelo enunciado).

### Etapa 9.2 — Diagramas
- `documentacao/arquitetura_fase4.md` (Mermaid): visão geral (3 serviços, 3 bancos, RabbitMQ, ingress, observabilidade), sequência da Saga, compensações. Exportar PNG para o PDF de entrega.

### Etapa 9.3 — Collection Postman
- `documentacao/postman/oficina-fase4.postman_collection.json` + environment `local`: login, cadastro de cliente e veículo, abertura de OS, ações do mecânico, resposta ao orçamento, simulação de pagamento, consulta de status/histórico/saga. Com scripts que guardam IDs entre as requisições.

### Etapa 9.4 — Roteiro do vídeo (até 15 min)
- `documentacao/roteiro_video.md`, com tempos:
  1. Arquitetura (1 min).
  2. Fluxo completo de uma OS pelos 3 serviços, via Postman, com Mercado Pago sandbox real e túnel (5 min).
  3. Falha e compensação: execução recusada → estorno → cancelamento, mostrando a saga e o histórico (3 min).
  4. Deploy automatizado: PR → CI → merge → CD no kind com smoke test verde (3 min).
  5. Monitoramento: trace no Jaeger e dashboard da Saga no Grafana (2 min).
- Checklist pré-gravação (plataforma no ar, seeds, túnel ativo, flag de falha).

---

## 15. Checklist final contra o enunciado

- [ ] ≥ 3 microsserviços, cada um com repo, infra (manifests + banco próprio) e banco próprios
- [ ] ≥ 1 banco SQL (Postgres) e ≥ 1 NoSQL (MongoDB)
- [ ] REST síncrono + mensageria assíncrona (RabbitMQ); nenhum acesso ao banco alheio
- [ ] Saga com rollback/compensação, documentada e justificada no README
- [ ] Testes unitários em todos os serviços; BDD de um fluxo completo; cobertura ≥ 80% por serviço
- [ ] SonarCloud no CI
- [ ] Pipeline independente por serviço: build, testes, qualidade, deploy em Kubernetes
- [ ] `main` protegida com PR e checagens obrigatórias
- [ ] Monitoramento (Prometheus/Grafana da Fase 3) + rastreamento distribuído (Jaeger)
- [ ] Integração Mercado Pago
- [ ] Por repo: código, Dockerfile, manifests K8s, CI/CD, evidência de cobertura no README, documentação da arquitetura, Swagger/Postman
- [ ] Vídeo ≤ 15 min com os 4 itens exigidos
- [ ] PDF: participantes, links, vídeo, diagrama, estratégia da Saga, justificativas
