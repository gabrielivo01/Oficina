# Progresso — Fases 5–9 (Saga, qualidade e entregáveis)

Registro de execução de `documentacao/plano_fase4_implementacao.md` (Plano 1). Lido no início de cada fase; mantido conciso.

## Status geral
- **Situação:** Plano 1 concluído (tags `fase-0` a `fase-9`). Os 4 repositórios estão no GitHub e os pipelines
  rodaram de verdade: CI verde nos 3 serviços (incluindo testes de integração com Testcontainers e Quality Gate do
  SonarCloud) e CD verde nos 3 (deploy no kind e smoke test E2E da Saga até `FINALIZADA`/`CONCLUIDA`).
- **Branch `main` protegida** nos 3 serviços (PR obrigatório, CI obrigatório, sem push direto): mudanças agora
  entram por pull request.
- **Atualizado em:** 2026-11-16
- **(histórico) Fase 3 em implementação local (sem commit):** criado `oficina-execution-service`, implementadas as etapas 3.1–3.6 e adicionados testes unitários/mockados direcionados. Os testes unitários/mockados selecionados passaram (25 no Execution Service e 54 no OS Service); não foram executados containers, Testcontainers, serviços externos ou testes de integração.
- O diretório do Execution Service ainda não é um repositório Git separado nem tem remoto. O script de configuração de hooks agora detecta isso e não altera a configuração do repositório pai.
- **Fase 4 concluída localmente (sem commit):** implementados orçamento, cobrança, gateways fake/Mercado Pago, webhook, mensageria e notificações; o OS Service passou a fornecer e-mail do cliente e deixou de manter o pagamento legado. Testes unitários/mockados e compilação dos dois serviços passaram. Não foram executados containers, Testcontainers, serviços externos ou testes de integração.
- **Continuação até as fases 6–9 (sem commit):** adicionados testes de domínio/serviços/outbox, workflows de CI e validação estática da infraestrutura, dashboards/alertas/monitores, documentação da Saga, diagramas Mermaid, collection/environment Postman e roteiro de vídeo. Nenhum workflow remoto foi disparado; nenhum serviço, container, cluster ou teste de integração foi iniciado.
- **Cobertura unitária após `verify` filtrado:** OS 81,37% (1385/1702 linhas), Execution 80,78% (643/796) e Billing 80,40% (759/944). As três suítes passaram sem falhas.

## Complemento de 2026-11-16 (lacunas do Plano 1)
Revisão do código contra o plano encontrou lacunas, agora implementadas (sem commit; mensagens propostas por etapa):
- **2.7 / seção 3:** perfil Maven `integracao` (failsafe) nos 3 repos; os testes com Testcontainers do `oficina`
  viraram `*IT` (`MensageriaIntegracaoIT`, `MigracoesPostgresIT`, `RastreamentoIntegracaoIT`). `./mvnw verify` puro
  não exige Docker; CIs rodam `verify` + job `verify -Pintegracao`. ADR 0013 (estratégia de testes em dois planos;
  o 0010 previsto no plano já estava em uso).
- **5.4:** Saga em memória (`src/test/java/.../saga/memoria`): repositórios em memória, `BarramentoEmMemoria`
  (porta `PublicadorMensagens`) e participantes falsos de Execution/Billing respondendo com os JSON do catálogo;
  `SagaEmMemoriaTest` cobre caminho feliz, orçamento recusado, pagamento rejeitado, pagamento expirado, execução
  recusada, eventos duplicados e cancelamento manual.
- **6.1:** BDD reescrito sobre a Saga em memória; steps falam com a interface `ComunicacaoSaga` (Plano 2 acrescenta a
  implementação contra os serviços reais). 4 cenários passando.
- **Execution (3.1):** teste de contexto (H2 modo PostgreSQL + Flyway), `@DataJpaTest` dos repositórios com consulta
  própria (inclui `@Version`), teste unitário do listener. O índice parcial do outbox (sem suporte no H2) saiu da V1
  para `db/vendor/{postgresql,h2}/V3` — a V1 nunca foi aplicada (serviço ainda não implantado).
- **Billing (4.1/4.4):** ADR 0001 no repo (atomicidade e índices), teste de contrato (comandos consumidos pelo
  processador real; eventos produzidos pelos serviços reais comparados aos exemplos), adapter Mercado Pago contra
  WireMock HTTPS, teste de contexto e do listener.
- **Bug encontrado pelo teste de contexto do Billing:** no Spring Boot 4 `spring.data.mongodb.uri` não é mais lido;
  corrigido para `spring.mongodb.uri` (aceitando `SPRING_DATA_MONGODB_URI` por compatibilidade), com teste de regressão.
- **Contrato:** `OrcamentoRecusado` do Billing já enviava `valorTotal`; catálogo e exemplos atualizados (campo opcional).
- **7.1/7.3:** `cd.yml` nos 3 serviços (GHCR → kind → deploy → rollout → health → smoke E2E → logs em falha), overlay
  `k8s/overlays/local` do `oficina`, `oficina-infra-k8s/scripts/deploy_servicos_kind.sh` e `smoke_test_e2e.sh`
  (escritos e validados com `bash -n`/parser YAML; **não executados**).
- **Git:** `oficina-execution-service` e `oficina-billing-service` agora são repositórios (`main`, hooks configurados), sem commit.
- **Validação:** `./mvnw -q clean verify` sem Docker passou nos 3 repos (heap limitado a 700 MB por causa da memória do
  WSL). Cobertura LINE: OS 88,5%, Execution 86,8%, Billing 84,6%. `kubectl kustomize` dos overlays `local` e da plataforma OK.

## Validação real (2026-11-16)
- **Publicação:** `Oficina` e `oficina-infra-k8s` atualizados; `oficina-execution-service` e `oficina-billing-service`
  criados no GitHub (públicos). Imagens dos 3 serviços públicas no GHCR.
- **SonarCloud:** organização `gabrielivo01` e projetos criados, `SONAR_TOKEN` nos 3 repos. Quality Gate OK nos 3
  depois de: actions de terceiros fixadas por SHA, `curl` com HTTPS obrigatório, secrets fora de blocos `run`, CSRF
  desabilitado justificado (APIs stateless com JWT) e exclusões de cobertura iguais às do JaCoCo. Cobertura no
  SonarCloud (linhas + condições): OS 83,0%, Execution 82,2%, Billing ≈ 83% após novos testes (era 78,7%).
- **Testes de integração do OS:** `MigracoesPostgresIT` e `RastreamentoIntegracaoIT` alinhados à Saga (cliente com
  e-mail, cancelamento confirmado por `ReservaPecasLiberada`, trace medido em `POST /entregar`); verdes no CI.
- **CD:** problemas encontrados só no deploy real e corrigidos: Secret errado nas credenciais do MongoDB do Billing e
  health de e-mail derrubando o `/actuator/health` (MailHog não existe no kind); smoke test passou a usar o
  readiness. Saga validada no Kubernetes com os 3 serviços, RabbitMQ, PostgreSQL e MongoDB (replica set).
- **Proteção de branch:** `scripts/proteger_branch_main.sh` corrigido (nomes reais das checagens e aprovações
  configuráveis, padrão 0 por haver um único mantenedor) e aplicado.

## Itens para o Plano 2 (consolidado)
1. Testes `*IT` próprios no Execution (PostgreSQL/Flyway real, RabbitMQ) e no Billing (MongoDB replica set com
   transação, RabbitMQ); o perfil `integracao` já existe e roda no CI.
2. Implementação de `ComunicacaoSaga` contra os serviços reais e BDD rodando contra o kind (hoje o smoke test cobre
   o caminho feliz no Kubernetes; as compensações são cobertas em memória).
3. Verificar traces ponta a ponta no Jaeger (HTTP → outbox → RabbitMQ → consumidor nos 3 serviços).
4. Mercado Pago sandbox real (Checkout Pro, webhook com assinatura via túnel, estorno).
5. Ferramentas estáticas: `actionlint`, `kubeconform` e `promtool` nos workflows dos serviços (a infra já roda
   shellcheck/kustomize/kubeconform no `validate.yml`).
6. Dívida: o Billing trata JSON inválido como `IllegalArgumentException` (vai para a DLQ após as retentativas, em vez
   de rejeitar na hora como o Execution faz com `AmqpRejectAndDontRequeueException`).

## Fases concluídas

### Fase 0 — Padronização e ADRs (repo `oficina`)
- **Entregue (antes desta execução automatizada):** ArchUnit com FreezingArchRule (`26c68c0`), guia do projeto + regras de domínio (`4e73905`),
  Spotless palantir (`e4cf77f`, `ffdead9`), JaCoCo com limite + `verify` no CI e no pre-push (`d0e4c2e`),
  hooks de formatação e compilação (`9cc55ac`), skill `novo-caso-de-uso` (`75e17ff`), subagent `revisor` (`408f752`),
  ADRs 0004–0009 (`9f27d44`), hook `commit-msg` (`0dc906b`), Prometheus/Grafana no compose (`834d4e8`),
  `APP_INTERNAL_API_KEY` no compose (`8b84232`), plano versionado (`1e04ad5`).
- **Validação:** `./mvnw -q verify` verde em 2026-11-15 (sem `[ERROR]`); ADRs 0004–0009 presentes.
- **Tag:** `fase-0` (oficina).

### Fase 1 — Plataforma local (repo `oficina-infra-k8s`, branch master)
- **Entregue:** `local/docker-compose.yml` (RabbitMQ 5672/15672, Postgres OS 5432, Postgres Execution 5433,
  MongoDB 27017 em replica set `rs0` de 1 nó sem auth, Jaeger 16686, OTel Collector 4317/4318, MailHog 1025/8025;
  healthcheck em todos); `local/rabbitmq/definitions.json` (fonte única p/ Compose e kind: `oficina.comandos` direct,
  `oficina.eventos` topic, `oficina.dlx`, filas quorum `execution.comandos`, `billing.comandos`, `os.saga.eventos`
  com `x-delivery-limit: 3` → `*.dlq`; usuário `oficina/oficina`, sem guest); `kind/cluster.yaml`;
  `k8s/plataforma/` (Kustomize: RabbitMQ, Jaeger, OTel, Ingress, ingress-nginx v1.15.1 + patches);
  `scripts/plataforma_up.sh`/`plataforma_down.sh`; perfil `OBSERVABILITY_PROFILE=kind` no
  `install_observability_stack.sh`; README e guia do projeto; hook `commit-msg`.
- **Endpoints internos no kind:** `rabbitmq.plataforma.svc:5672`, `otel-collector.plataforma.svc:4317|4318`;
  UIs em `http://{grafana,jaeger,rabbitmq}.localtest.me:8081` (Grafana admin/admin).
- **Commits:** `821f81f` (1.1), `96876e9` (1.2), `cc5bcfe` (1.3), `2d5819f` (fix: RabbitMQ como uid 100 — cookie
  Erlang criado como root pelo healthcheck quebrava a 1ª subida), `1cfce7c` (docs).
- **Validação (coordenador):** Compose do zero com 7/7 healthy, 6 filas + bindings conferidos via API, Jaeger 200;
  kind (passos do script executados um a um) com todos os pods Running e Grafana/Jaeger/RabbitMQ 200 via ingress.
  Validador independente: todos os critérios ATENDIDO; só achados de baixa severidade (ver dívidas).
- **Tag:** `fase-1` (oficina-infra-k8s e oficina).

### Fase 2 — Preparar o OS Service (`oficina`)
- **Conclusão informada pelo usuário:** catálogo/estoque retirados do OS Service, contratos e mensageria preparados.
- A validação final de integração da fase não foi repetida nesta execução por instrução do usuário para evitar processamento custoso.

### Fase 3 — Execution Service e migração de estoque
- **Implementado localmente:** serviço Spring Boot com domínio de peças, reservas idempotentes, tarefas de diagnóstico/reparo, contratos de mensageria, outbox/inbox, JWT e manifests Kustomize; removido o catálogo do OS Service, com itens como snapshots externos.
- **Documentação:** README do serviço, guia de agente/skill e diagrama atualizado para o fluxo atual; conexão com a Saga entregue na Fase 5.
- **Validação:** testes unitários/mockados direcionados passaram e compilaram o código de produção e os testes. Não foram executados containers, Testcontainers, banco, broker, Kubernetes ou testes de integração.
- **Git:** sem commit; pasta do serviço ainda sem repositório Git próprio.

### Fase 4 — Billing Service
- **Implementado localmente:** Billing com orçamento, cobrança, gateway fake e Mercado Pago, validação de webhook, inbox/outbox, notificações por e-mail e manifests Kustomize; removido o fluxo legado de pagamento do OS Service e adicionada migration. O e-mail do cliente foi incluído no modelo e contrato usado pelo Billing.
- **Documentação/deploy:** ADR de transações Mongo, README e script de Secret; o script está executável. Os manifests foram renderizados com `kubectl kustomize`, sem aplicá-los.
- **Validação:** `./mvnw -q test` no Billing Service e testes focados `MapperTests,ClienteServiceTest,ClienteRequestValidationTest,ContratosMensagensTest` no OS Service passaram; compilação de produção dos dois serviços e verificações Spotless passaram. `kubectl kustomize` e `bash -n` passaram sem aplicar manifests ou executar o script. Não foram iniciados containers ou serviços externos, nem executados testes de integração.
- **Git:** sem commit; Billing Service ainda não tem repositório Git próprio.

### Fase 5 — Saga orquestrada
- **Implementado localmente (etapas 5.1–5.3):** máquina de estados persistida, transações OS/outbox, processamento idempotente de eventos, prazos e retries de compensação, consulta da Saga, métricas e remoção das transições manuais de aprovação/avanço.
- **Compensação de cancelamento manual:** foi incluído `CancelarExecucao`/`ExecucaoCancelada`; o Execution Service cancela tarefas não finalizadas e devolve ao estoque as peças já consumidas. Se a finalização vencer a corrida, a OS segue para `FINALIZADA`. A migration V2 do Execution atualiza o constraint de status.
- **Contratos e alertas:** documentados `CobrancaCancelada`, `CancelarExecucao` e `ExecucaoCancelada`; fixtures de contrato e alerta Prometheus para falha de compensação atualizados. O Execution agora publica eventos com routing keys `execution.<evento-em-kebab-case>`, alinhadas ao catálogo.
- **Validação final:** `spotless:check` passou nos três serviços. Passaram testes unitários/mockados focados de OS (`SagaOrdemServicoTest`, `OrquestradorSagaServiceTest`, `OrdemServicoTest`, `OrdemServicoServiceTest`, `OrdemServicoSnapshotValidationTest`), Execution (`OutboxServiceTest`, `TarefaExecucaoServiceTest`, `ReservaPecasServiceTest`, `ProcessadorComandosServiceTest`) e Billing (`CobrancaServiceTest`). Não foram executados Testcontainers, containers, serviços externos ou testes de integração; a etapa 5.4 permanece adiada.
- **Git:** sem commit.

### Fase 6 — BDD, cobertura e qualidade
- **BDD:** quatro cenários Cucumber em português cobrem caminho feliz e compensações no domínio puro da Saga. Não usam Testcontainers nem participantes reais; a parte distribuída da etapa 5.4 continua adiada.
- **Cobertura:** testes significativos de orquestração/saga, tarefas e reservas, cobrança, gateways fake e outbox elevaram a cobertura unitária para 81,37% (OS), 80,78% (Execution) e 80,40% (Billing). Limite JaCoCo configurado em 80% nos três serviços.
- **SonarCloud:** propriedades e gate configurados nos três repos; organização/projetos e `SONAR_TOKEN` continuam dependendo de configuração externa do usuário. Análise Sonar não executada.

### Fase 7 — CI/CD e proteção de branch
- **CI:** workflows dos três serviços executam `verify` com seleção/exclusão explícita dos testes de integração; o CI do OS publica o relatório Cucumber usando o caminho correto. Build de imagem ocorre apenas no runner do GitHub, não nesta sessão.
- **Infra:** workflow `validate.yml` verifica workflows/scripts e renderiza manifests com Kustomize/kubeconform; foi mantido estático e não cria cluster kind.
- **Proteção:** `scripts/proteger_branch_main.sh` aplica proteção PR/checagens requeridas via `gh`, mas não foi executado porque altera configuração remota e requer permissão administrativa.
- **Pendente por restrição:** CD em kind e smoke test E2E não foram adicionados nem executados, pois implantariam serviços e fariam validação integrada.

### Fase 8 — Observabilidade
- Dashboards OS/Execution/Billing/Saga, ServiceMonitor RabbitMQ, alertas de Saga em falha, DLQ, disponibilidade, 5xx e latência foram versionados.
- Kustomize dos overlays de plataforma e serviços foi renderizado localmente sem apply. Os YAMLs passaram por parser; PromQL ainda não foi verificado por `promtool` (indisponível), e nenhum trace/fluxo real foi produzido.

### Fase 9 — Documentação e entregáveis
- Readmes atualizados com o estado atual da Saga, links para documentação/coleção e restrições de execução.
- Criados diagramas Mermaid em `documentacao/arquitetura_fase4.md`, collection e environment Postman em `documentacao/postman/` e roteiro/checklist em `documentacao/roteiro_video.md`.
- PDF final e vídeo não foram gerados: faltam dados dos participantes/links e gravação do fluxo implantado; nenhum fluxo ou serviço foi iniciado para produzir evidências.

### Validação local segura desta continuação
- OS: `./mvnw -q clean -Dtest='*Test,!*IntegracaoTest,!MigracoesPostgresTest,!TopologiaRabbitMqSincronizadaTest' verify`.
- Execution/Billing: `./mvnw -q clean -Dtest='*Test,!*IntegrationTest,!*IntegracaoTest' verify`.
- JSON Postman parseado, scripts passaram `bash -n`, overlays foram renderizados com `kubectl kustomize`. Nenhum `apply`, container, serviço, kind, teste de integração ou workflow remoto foi executado.

## Decisões autônomas
| Decisão | Motivo | Fase |
|---|---|---|
| Pasta de trabalho real é `~/git/oficina-project` (o briefing citava `~/oficina-workspace`). Todos os repos ficam lá, como irmãos de `oficina`. | É onde os repos existem; o prompt do usuário define essa pasta. | 0 |
| As instruções do cabeçalho do plano ("uma fase por sessão", "espere aprovação", "pare e pergunte") são substituídas pela execução autônoma pedida pelo usuário; conflitos com o código real são resolvidos pela opção mais alinhada às seções 1 e 2 e registrados aqui. | Instrução explícita do usuário para esta execução. | 0 |
| `actionlint`, `shellcheck`, `kubeconform` e `promtool` não estão instalados no host. A checagem de infraestrutura foi preparada para executar validadores estáticos no GitHub Actions; não usar Docker/kind local nesta tarefa. | Respeitar a restrição de não iniciar containers ou serviços. | 7–8 |
| Ingress do kind em `127.0.0.1:8081/8443` (não 80/443). | Porta 80 do host ocupada por um nginx local. | 1 |
| Filas quorum com DLX `oficina.dlx` e `x-delivery-limit: 3`; o backoff entre tentativas fica no consumidor (Spring AMQP, Fases 2–4). Serviços não redeclaram filas com argumentos diferentes. | Retry/DLQ da seção 3.3 com topologia versionada num só lugar. | 1 |
| MongoDB de dev em replica set de 1 nó sem autenticação (`mongodb://localhost:27017/billing_db?directConnection=true`). | Permite transação Mongo no Billing (Fase 4); auth em RS exige keyfile, desnecessário em dev. | 1 |
| ingress-nginx mantido (v1.15.1, manifest oficial versionado + patches `nodeSelector ingress-ready` e `worker-processes: 2`). | O plano pede; 16 workers estouravam memória. Descontinuação registrada como dívida. | 1 |
| Perfil `kind` (opt-in) no `install_observability_stack.sh`; o script passa a criar o namespace `oficina`. Uso padrão (EKS) inalterado. | Reaproveitar o script, como pede a etapa 1.2, cabendo em ~4 GB. | 1 |
| Scripts validados executando seus comandos um a um (não como script). | Permissão nega `./scripts/*` e `bash scripts/*`. | 1 |

## Pendências para o usuário
- **Permissão para executar scripts:** nas permissões locais do ambiente de execução as regras `Bash(./scripts/:*)` e `Bash(bash scripts/:*)` não casam com `./scripts/plataforma_up.sh` (negado). Ajuste a regra (ex.: `Bash(./scripts/*)`, `Bash(bash scripts/*)`) e rode `scripts/plataforma_up.sh` / `plataforma_down.sh` uma vez como script. Impacta também a validação local do CD (Fase 7).
- Os containers do compose do repo `oficina` (app, postgres, mailhog, prometheus, grafana) foram parados (`stop`) na Fase 1 para liberar portas/memória; para voltar: `docker compose up -d` em `oficina`.

## Problemas conhecidos / dívidas
- Docker do host (WSL2) tem ~3,9 GB de memória: o cluster kind com kube-prometheus-stack + RabbitMQ + Jaeger + 3 serviços e bancos pode ficar sem memória. Se necessário, aumentar a memória do WSL (`.wslconfig`, `memory=8GB`). Medido na Fase 1: plataforma kind ≈ 2,2 GB.
- (Fase 1) Tags de imagem fixas só em minor (`rabbitmq:4.3-management-alpine`, `postgres:16`, `mongo:8.0`) e imagem do nó kind dependente da versão do kind no runner.
- (Fase 1) `aguardar_pods` do `plataforma_up.sh` espera o cluster inteiro: na Fase 7, um pod de serviço não pronto segura a espera — restringir aos namespaces da plataforma ao adaptar o CD.
- (Fase 1) DLQs com `delivery-limit` padrão (20) e sem DLX; ingress-nginx descontinuado upstream (última versão mar/2026).
- (Fase 1) Exemplo de Actions no README faz checkout do `oficina-infra-k8s`: se o repo ficar privado, exige token.
- (Fase 1) Decisões de infra da Fase 1 registradas aqui e no README, sem ADR próprio (ADR 0008 cobre o ambiente kind).
- Commit antigo `621d864` (oficina-infra-k8s, anterior à Fase 4) contém `Co-Authored-By` na mensagem; não reescrito (proibido alterar histórico).
