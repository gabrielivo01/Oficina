# Progresso — Fase 4 (Microsserviços + Saga)

Registro de execução de `documentacao/plano_fase4.md`. Lido no início de cada fase; mantido conciso.

## Status geral
- **Fase atual:** 2 — Preparar o OS Service (`oficina`)
- **Última fase concluída:** 1 — Plataforma local
- **Atualizado em:** 2026-11-15 20:40

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

## Decisões autônomas
| Decisão | Motivo | Fase |
|---|---|---|
| Pasta de trabalho real é `~/git/oficina-project` (o briefing citava `~/oficina-workspace`). Todos os repos ficam lá, como irmãos de `oficina`. | É onde os repos existem; o prompt do usuário define essa pasta. | 0 |
| As instruções do cabeçalho do plano ("uma fase por sessão", "espere aprovação", "pare e pergunte") são substituídas pela execução autônoma pedida pelo usuário; conflitos com o código real são resolvidos pela opção mais alinhada às seções 1 e 2 e registrados aqui. | Instrução explícita do usuário para esta execução. | 0 |
| `actionlint`, `shellcheck` e `kubeconform` não estão instalados no host; serão executados via imagens Docker oficiais (`rhysd/actionlint`, `koalaman/shellcheck`, `ghcr.io/yannh/kubeconform`). | Não alterar arquivos fora da pasta de trabalho (sem instalar binários no host). | 0 |
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
