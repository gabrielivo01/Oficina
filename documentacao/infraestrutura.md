Infraestrutura e conteinerização

> **Nota sobre topologia (pós-split em 4 repositórios):** este repositório
> (`oficina-app`) não possui mais Terraform próprio. O cluster EKS e o API
> Gateway vivem no repositório `oficina-infra-k8s`; o RDS PostgreSQL vive no
> repositório `oficina-infra-db`; a function de autenticação por CPF vive no
> repositório `oficina-auth-lambda`. Ver
> `documentacao/plano_implementacao.md` (seção 5) e
> `documentacao/diagrama_componentes.md` para o mapeamento completo. As
> seções abaixo descrevem o que **este** repositório ainda possui
> (containerização, manifests Kubernetes da aplicação, observabilidade
> ao nível de código) e apontam para os outros repositórios onde relevante.

Escopo deste repositório:
- externalização de configuração para execução local, Docker e Kubernetes
- inclusão de actuator para health checks
- execução local com `docker-compose` incluindo aplicação, PostgreSQL e MailHog
- manifestos Kubernetes da aplicação (Deployment, Service, ConfigMap, Secret, HPA)
- overlays Kubernetes para `dev`, `hml` e `prod`

Arquivos relevantes:
- `Dockerfile`
- `docker-compose.yml`
- `src/main/resources/application.properties`
- `src/main/resources/application-docker.properties`
- `src/main/resources/application-k8s.properties`
- `k8s/base/*.yaml`
- `k8s/overlays/*`

Como executar localmente com Docker Compose:

```bash
docker compose up --build
```

URLs esperadas:
- aplicação: `http://localhost:8080`
- swagger: `http://localhost:8080/swagger-ui.html`
- mailhog: `http://localhost:8025`

Como aplicar no Kubernetes (assume que o cluster já foi provisionado pelo
repositório `oficina-infra-k8s`):

```bash
kubectl apply -k k8s/overlays/dev
```

Estratégia por ambiente no Kubernetes:
- `dev`: usa PostgreSQL in-cluster para facilitar teste local
- `hml`: usa imagem de registry e remove o PostgreSQL in-cluster (usa o RDS do repositório `oficina-infra-db`)
- `prod`: mesma estratégia de `hml`, com segredos vindos de pipeline/secret manager

Integração com AWS Secrets Manager (hml/prod):
- o `ClusterSecretStore` é provisionado pelo repositório `oficina-infra-k8s`
  (`k8s/external-secrets/clustersecretstore-aws.yaml` naquele repo)
- overlays `hml` e `prod` (neste repositório) usam `ExternalSecret` para gerar o `oficina-secret`
- chaves esperadas no Secrets Manager:
	- `/oficina/<ambiente>/app` propriedades `JWT_SECRET` e `APP_INTERNAL_API_KEY`
	- `/oficina/<ambiente>/database` propriedade `PASSWORD`
	- `/oficina/<ambiente>/mail` propriedades `USERNAME` e `PASSWORD`

`APP_INTERNAL_API_KEY` deve ter o **mesmo valor** configurado no repositório
`oficina-auth-lambda` (variável `internal_api_key` do Terraform daquele
repo) — é a chave que `InternalApiKeyFilter` exige no header
`X-Internal-Api-Key` para o endpoint `GET /internal/clientes/{cpf}/status`.

Integração entre repositórios (cluster/banco → aplicação):
1. Aplicar `oficina-infra-k8s` (cluster) e `oficina-infra-db` (banco, usando
   `vpc_id`/`subnet_ids` do passo anterior).
2. Coletar os outputs `db_endpoint`, `db_port`, `db_name`, `db_username` do
   repositório `oficina-infra-db`.
3. Garantir que os segredos existam no AWS Secrets Manager para o `ExternalSecret`.
4. Renderizar/aplicar o overlay deste repositório com esses valores (ver
   `scripts/render_k8s_overlay.sh`/`scripts/deploy_k8s_overlay.sh` abaixo).
5. Publicar o hostname do `Service` `oficina-app-lb` (`app_public_url`) de
   volta nos repositórios `oficina-infra-k8s` e `oficina-auth-lambda`.

Automação ponta a ponta (render + apply + smoke test):

```bash
DB_ENDPOINT="..." DB_PORT="5432" DB_NAME="oficina_db" DB_USERNAME="postgres" \
scripts/deploy_k8s_overlay.sh hml
```

Scripts disponíveis (todos operam só sobre k8s — nenhum executa Terraform
neste repositório):
- `scripts/render_k8s_overlay.sh`: renderiza o overlay usando os valores de
  banco passados via `DB_ENDPOINT`/`DB_PORT`/`DB_NAME`/`DB_USERNAME`
- `scripts/deploy_k8s_overlay.sh`: renderiza, aplica no cluster, aguarda
  rollout e executa smoke test
- `scripts/ci_deploy.sh`: wrapper usado pelo workflow `deploy-app.yml`

Smoke test autenticado (opcional):
- habilitar com `AUTH_SMOKE_ENABLED=true`
- parâmetros opcionais: `AUTH_SMOKE_LOGIN_URL`, `AUTH_SMOKE_TARGET_URL`, `AUTH_SMOKE_LOGIN`, `AUTH_SMOKE_PASSWORD`
- em falha, o deploy faz rollback automático por padrão

Smoke test de banco (opcional):
- habilitar com `DB_SMOKE_ENABLED=true`
- endpoint padrão: `/actuator/health/db`
- em falha, o deploy também dispara rollback automático por padrão

Proteções adicionadas no deploy:
- validação de manifesto renderizado com `kubectl kustomize`
- rollback automático do deployment `oficina-app` em falha de smoke test

Workflows CI/CD deste repositório:
- `.github/workflows/ci.yml` — build da aplicação, testes automatizados, build da imagem Docker (todo push/PR).
- `.github/workflows/plan-app.yml` — disparo manual: roda os testes, renderiza um overlay e valida com `kubectl kustomize`, sem aplicar nada; publica o overlay renderizado como artifact.
- `.github/workflows/deploy-app.yml` — disparo manual: build/push da imagem no GHCR, depois deploy do overlay (`hml`/`prod`) via `scripts/ci_deploy.sh`; usa `environment` dinâmico (`hml`/`prod`) para permitir aprovação obrigatória em produção.

Configuração recomendada no GitHub (`oficina-app`):
1. criar environments `hml` e `prod`
2. no environment `prod`, habilitar required reviewers para aprovação manual antes do job
3. cadastrar os segredos de smoke autenticado quando for habilitar `AUTH_SMOKE_ENABLED=true`
4. cadastrar `DB_ENDPOINT`, `DB_PORT`, `DB_NAME`, `DB_USERNAME` (outputs do
   repositório `oficina-infra-db`), `AWS_REGION`, `AWS_ACCESS_KEY_ID`,
   `AWS_SECRET_ACCESS_KEY`, `EKS_CLUSTER_NAME` (do repositório
   `oficina-infra-k8s`)

Próximos passos recomendados:
1. publicar a imagem em registry e ajustar os overlays com a tag do pipeline
2. endurecer a rede e a estratégia de banco para produção
3. validar `kubectl apply -k` em ambiente com tooling instalado
4. nos outros 3 repositórios: validar `terraform plan/apply` reais (não foi
   possível neste ambiente de trabalho — ver observações de escopo em cada
   README)

Autenticação por CPF e API Gateway (código deste repositório):
- Endpoint interno `GET /internal/clientes/{cpf}/status`, protegido por
  `InternalApiKeyFilter` (header `X-Internal-Api-Key`, propriedade
  `app.internal.api-key`) — consumido pelo Lambda do repositório
  `oficina-auth-lambda`.
- `TipoPrincipal`/`ClienteUserDetailsService`/`JwtAuthFilter` — aceitam tanto
  o JWT de staff (login/senha, claim `tipo` ausente) quanto o JWT de cliente
  emitido pelo Lambda (claim `tipo=CLIENTE`).
- `k8s/base/app-service-external.yaml`: `Service` `oficina-app-lb`
  (`type: LoadBalancer`), expõe a aplicação publicamente via Classic ELB
  provisionado pelo provider AWS in-tree do EKS provisionado pelo repositório
  `oficina-infra-k8s`. O `Service` `oficina-app` original (`ClusterIP`)
  continua existindo para tráfego/smoke tests internos ao cluster.
- O Terraform do Lambda (`auth-lambda`) e do API Gateway (`api-gateway`) — e
  a decisão de mantê-los sem JWT authorizer no edge, e o fato de
  `/internal/**` ficar tecnicamente alcançável pelo Gateway (mitigado só pela
  API key) — estão documentados nos repositórios `oficina-auth-lambda` e
  `oficina-infra-k8s`, respectivamente, e em
  `documentacao/rfcs/0003-estrategia-de-autenticacao.md` neste repositório.

Observabilidade (Prometheus + Grafana):
- Dependências no `pom.xml`: `micrometer-registry-prometheus` (expõe
  `/actuator/prometheus`) e `net.logstash.logback:logstash-logback-encoder`
  (logs JSON estruturados).
- `management.endpoints.web.exposure.include` inclui `prometheus`;
  `management.metrics.distribution.percentiles-histogram.http.server.requests=true`
  habilita os buckets usados pelo painel de latência p95.
- `src/main/resources/logback-spring.xml`: perfis `docker`/`k8s` logam em JSON
  (via `LogstashEncoder`, inclui todo o MDC automaticamente); demais perfis
  (incluindo o default local) logam texto legível com o `correlationId` visível.
- `infrastructure/observability/CorrelationIdFilter`: propaga/gera
  `X-Correlation-Id` por requisição via MDC — correlaciona todas as linhas de
  log de uma mesma requisição, e a mesma correlação pode ser repassada entre
  serviços (o header é ecoado na resposta).
- Métricas de negócio custom (Micrometer, expostas em `/actuator/prometheus`):
  - `oficina_ordens_servico_abertas_total` — incrementada em `OrdemServicoService.abrir`.
  - `oficina_ordens_servico_tempo_no_status_seconds{status=...}` — Timer
    registrado a cada transição de status (`avancarStatus`, `responderOrcamento`,
    `atualizarStatusExterno`), usando o campo `OrdemServico.statusDesde`
    (não confundir com `atualizadoEm`, que também muda em alterações de itens).
  - `oficina_notificacoes_falhas_total{canal="email"}` — incrementada em
    `EmailStatusNotificationPort` quando `mailSender.send` falha (a exceção
    ainda é relançada; a métrica não muda o comportamento existente).
- `k8s/base/app-service.yaml`: tem `metadata.labels.app` (para
  `ServiceMonitor` selecionar o Service, provisionado no repositório
  `oficina-infra-k8s`), porta nomeada `http`, e anotações
  `prometheus.io/scrape|path|port` (funciona mesmo sem o Prometheus Operator,
  para setups de Prometheus mais simples baseados em anotação).
- O `ServiceMonitor`, `PrometheusRule`, dashboard Grafana e o script de
  instalação do `kube-prometheus-stack` vivem no repositório
  `oficina-infra-k8s` (`k8s/observability/`,
  `scripts/install_observability_stack.sh`) — cluster-wide, não específicos
  desta aplicação.
- **não validado**: a instrumentação da aplicação foi testada de ponta a
  ponta (login, abrir OS, avançar status → métricas corretas em
  `/actuator/prometheus`); a stack Prometheus/Grafana em si (Helm/manifests
  no repositório `oficina-infra-k8s`) não foi executada contra um cluster
  real (`helm`/`kubectl` indisponíveis no ambiente onde foi escrita).
