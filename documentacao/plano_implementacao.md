# Plano de Implementação — Tech Challenge (Operação Corporativa)

Baseado em `implementation_instruction.md` (enunciado do desafio) e no estado atual do
repositório. Este documento organiza o trabalho restante em fases sequenciáveis,
apontando o que já existe, o que falta, e em quais arquivos/pastas cada item deve
pousar — respeitando a arquitetura Clean/DDD e os padrões já estabelecidos em
`CLAUDE.md` (ports pattern, `application → domain`, idioma português no domínio).

## 0. Diagnóstico — o que já existe vs. o que falta

| Requisito do desafio | Status atual | Gap |
|---|---|---|
| Autenticação via login/senha + JWT | ✅ `AuthService`, `JwtService`, `JwtAuthFilter` | Autenticação é login/senha, **não** por CPF |
| Function Serverless de autenticação por CPF | ✅ repositório `oficina-auth-lambda` (seção 2) | Terraform ainda não validado (`terraform validate`) |
| API Gateway | ✅ módulo `api-gateway` no repositório `oficina-infra-k8s` (seção 3) | Roteamento sem authorizer no edge; `/internal/**` alcançável (mitigado por API key) |
| Banco gerenciado (Postgres) via Terraform | ✅ repositório `oficina-infra-db` | Repositório próprio, conforme exigido |
| Cluster Kubernetes + Terraform | ✅ repositório `oficina-infra-k8s` | Repositório próprio, conforme exigido |
| 4 repositórios separados com CI/CD próprio | ✅ seção 5 — executado (2026-09-15) | Repos preparados localmente, sem push/criação no GitHub ainda |
| Branch `main` protegida / PR obrigatório | ❌ configuração do GitHub, não feita | Só possível depois que os repos existirem no GitHub |
| Deploy automático em hml/prod | ⚠️ `deploy-app.yml`/`deploy-infra.yml` de cada repo são `workflow_dispatch` (manual) | Requisito pede deploy automático por branch — falta trigger `push`/`on: pull_request` + branch protection |
| Observabilidade (Prometheus/Grafana) | ✅ seção 4 | Stack Helm não validada contra cluster real |
| Logs estruturados (JSON) com correlação | ✅ `logback-spring.xml` + `CorrelationIdFilter` (seção 4) | — |
| Dashboards de negócio (volume de OS, tempo médio por status, erros) | ✅ `k8s/observability/grafana-dashboard-oficina.yaml` (seção 4) | Dashboard não renderizado em Grafana real |
| Diagrama de Componentes | ✅ `documentacao/diagrama_componentes.md` (Mermaid, renderização validada) | — |
| Diagrama de Sequência (auth + abertura de OS) | ✅ `documentacao/diagrama_sequencia_{auth,abertura_os}.md` | — |
| RFCs | ✅ `documentacao/rfcs/0001-0003` | — |
| ADRs | ✅ `documentacao/adrs/0001-0003` | — |
| Justificativa formal do banco + modelo ER | ✅ `documentacao/modelo_de_dados.md` (DBML + ER Mermaid + justificativa + relacionamentos) | — |
| `Cliente` com status consultável para auth | ✅ `StatusCliente` + endpoint interno (seção 1) | — |

## 1. Domínio: suporte a autenticação por CPF

O Lambda precisa "consultar a existência e o status do cliente". Hoje `Cliente`
(`domain/cliente/Cliente.java`) não tem conceito de status — só dados cadastrais.

1. Adicionar um `StatusCliente` (enum: `ATIVO`, `INATIVO`/`BLOQUEADO`) em
   `domain/cliente/`, seguindo o mesmo padrão de `StatusOrdemServico`.
2. Adicionar o campo ao `Cliente`, com regra de negócio: cliente novo nasce `ATIVO`;
   expor `estaApto()`/`isAtivo()` em vez de getter+setter solto (mesma filosofia de
   encapsulamento já usada na entidade).
3. Criar migration (Flyway/DDL, ver como o schema é gerenciado hoje — se é
   `ddl-auto` do Hibernate, ajustar `application*.properties` conforme padrão) e
   atualizar `DataSeeder` para popular o novo campo.
4. Expor endpoint interno **não-público** de consulta por CPF para o Lambda usar,
   em vez de dar acesso direto ao banco ao Lambda (mantém a fronteira do domínio):
   - `application/cliente/ClienteService`: novo método `consultarPorCpf(String cpf)`
     retornando existência + status (não retorna dados sensíveis além do necessário).
   - `presentation/cliente/`: novo endpoint, ex. `GET /internal/clientes/{cpf}/status`.
   - Proteger esse endpoint por rede (Security Group / API Gateway resource
     policy) ou por API key de serviço-a-serviço — **não** pelo mesmo JWT de
     usuário, já que é o próprio Lambda quem ainda não tem token nesse ponto.
5. Testes: `ClienteServiceTest`, novo `ClienteControllerTest` cobrindo o endpoint
   interno (cliente existente ativo / inativo / inexistente).

## 2. Function Serverless de autenticação (Lambda)

Novo componente, fora do monólito Spring — vira o repositório 1 da seção 5.

1. Runtime definido: **Java** (decisão confirmada, seção 8) — permite reusar a
   regra de `shared/util/CpfValidator.java` quase sem adaptação; aceitar o
   trade-off de cold start maior em troca de não duplicar lógica em outra
   linguagem.
2. Responsabilidades da function (mapeando 1:1 para o enunciado):
   - Validar formato do CPF (reaproveitar a mesma regra de dígito verificador de
     `CpfValidator`, extraída/copiada para o módulo do Lambda já que são
     deployables/repositórios diferentes).
   - Chamar o endpoint interno criado no passo 1 para checar existência/status.
   - Se apto, gerar um JWT **compatível** com o `JwtService` da aplicação (mesmo
     algoritmo, mesmas claims esperadas por `JwtAuthFilter`, mesmo segredo —
     compartilhado via AWS Secrets Manager, já usado hoje para `JWT_SECRET`).
3. Garantir que `JwtService`/`JwtAuthFilter` no lado Java não fiquem acoplados a
   "quem" emitiu o token — apenas validam assinatura/claims. Adicionar teste de
   integração que gera um JWT no formato do Lambda (mesmo segredo/claims) e
   confirma que `JwtAuthFilter` aceita.
4. Empacotamento: `serverless.yml` (Serverless Framework) ou SAM/Terraform
   (`aws_lambda_function` + `aws_apigatewayv2_api`) — manter Terraform para
   consistência com o resto do projeto, que já usa Terraform em todo lugar.
5. Tratamento de erro: CPF inválido → 400; cliente inexistente ou inativo → 401/403
   sem revelar qual dos dois motivos (evita enumeração de CPFs válidos).

## 3. API Gateway — implementado (MVP pragmático)

Produto definido: **AWS API Gateway** (decisão confirmada, seção 8) — menor
atrito dado que o resto da infra já é 100% AWS (EKS + RDS via Terraform).
Registrar formalmente como RFC (seção 6) mesmo com a decisão já tomada, para
documentar as alternativas descartadas (Kong/Traefik).

Implementado:
1. `k8s/base/app-service-external.yaml` — novo `Service` `oficina-app-lb`
   (`type: LoadBalancer`), expõe a app publicamente via Classic ELB do
   provider in-tree do EKS (sem precisar instalar o AWS Load Balancer
   Controller). O `Service` `oficina-app` (`ClusterIP`) original continua para
   tráfego interno/smoke tests.
2. `infra/terraform/modules/api-gateway/*` — HTTP API única do projeto:
   - `POST /auth/cpf` → integração `AWS_PROXY` com o Lambda (seção 2)
   - `ANY /{proxy+}` e `ANY /` → integração `HTTP_PROXY` para `oficina-app-lb`
     (repassa todas as demais rotas da aplicação principal)
3. `infra/terraform/modules/auth-lambda/*` foi simplificado para conter só o
   Lambda + IAM (Single Responsibility); o Gateway virou módulo próprio para
   poder rotear para múltiplos backends sem acoplar aos dois.
4. Wiring: `environments/dev/main.tf` ganhou `module.api_gateway` e a variável
   `app_public_url` (hostname do `oficina-app-lb`, só conhecido **depois** do
   `kubectl apply -k k8s/overlays/<env>` — fluxo de aplicação em duas etapas
   documentado em `documentacao/infraestrutura.md`).

Trade-offs conscientes (documentar como ADR, seção 6):
- Autenticação/autorização segue sendo decidida pela aplicação (`JwtAuthFilter`),
  não há JWT authorizer no edge do Gateway — o Gateway hoje só roteia, não
  valida token. Aceitável para o MVP; um authorizer no Gateway seria uma
  segunda camada redundante já que a app já rejeita tokens inválidos/expirados.
- `/internal/**` fica alcançável através do Gateway (protegido só pelo header
  `X-Internal-Api-Key`, não pela rede) porque o proxy é um catch-all. Hardening
  futuro: VPC Link privado + NLB interno, excluindo `/internal/**` do proxy
  público, ou Lambda anexado à VPC falando direto com o Service interno.
- Terraform destes módulos **não foi validado** com `terraform validate`/`plan`
  reais (CLI indisponível no ambiente onde foi escrito) — revisar antes do
  primeiro apply.

## 4. Observabilidade (Prometheus + Grafana)

1. **Instrumentação da aplicação**:
   - Adicionar `micrometer-registry-prometheus` ao `pom.xml` e expor
     `management.endpoints.web.exposure.include` incluindo `prometheus`
     (reaproveitando o Actuator já presente).
   - Subir Prometheus + Grafana no cluster (ex. Helm chart
     `kube-prometheus-stack`) — provisionado via os manifests/overlays em `k8s/`
     (ou no futuro repo `oficina-infra-k8s`, ver seção 5) usando um
     `ServiceMonitor`/`PodMonitor` apontando para o Service da app.
   - Grafana consome Prometheus como datasource; dashboards versionados como
     JSON/ConfigMap ao lado dos manifests de infra.
2. **Logs estruturados em JSON com correlação**:
   - Trocar/objetivar o encoder do Logback para JSON (`logstash-logback-encoder`).
   - Adicionar um filtro/`MDC` de `correlationId` (novo, em
     `infrastructure/`, ex. `infrastructure/observability/CorrelationIdFilter`)
     propagando um header (`X-Correlation-Id`) por requisição — coerente com o
     padrão de filtros já usado por `JwtAuthFilter`.
3. **Métricas de negócio para os dashboards pedidos**:
   - Volume diário de OS: contador Micrometer incrementado em
     `OrdemServicoService` na abertura (`abrirOrdemServico`).
   - Tempo médio de execução por status: `Timer`/gauge medindo transição entre
     status na máquina de estados de `OrdemServico`/`OrdemServicoService` (cada
     mudança de status já é centralizada ali, então é o ponto certo de instrumentar
     sem vazar preocupação de observabilidade para o domínio).
   - Erros/falhas em integrações: instrumentar `StatusNotificationPort`
     (implementações `EmailStatusNotificationPort`) e a chamada do endpoint
     interno de status de cliente usado pelo Lambda.
4. **Alertas**: falha no processamento de OS (exceções de domínio não tratadas,
   falhas de notificação) → alerta configurado na ferramenta de monitoramento,
   não no código Java.
5. **Healthchecks/uptime**: já existe Actuator; garantir que o gateway/monitoramento
   façam polling de `/actuator/health` e que liveness/readiness do `k8s/base/app-deployment.yaml`
   estejam alinhados (checar se já configurado; se não, adicionar probes).

## 5. Split em 4 repositórios com CI/CD próprio — executado (2026-09-15)

Executado localmente (repositórios novos como diretórios irmãos de
`oficina`, cada um com seu próprio `.git`, histórico novo — sem
`git filter-repo` — e **sem** criação/push no GitHub, por decisão do
usuário; ver ADR 0003 para os detalhes da execução). Mapeamento
efetivamente usado:

1. **`oficina-auth-lambda`** — código da function serverless (seção 2) + IaC do
   Lambda/API Gateway + pipeline (lint/test/deploy automático em push).
2. **`oficina-infra-k8s`** — hoje `infra/terraform/modules/eks-cluster` +
   `infra/terraform/modules/api-gateway` + `infra/terraform/environments/*`
   (parte de cluster) + manifests base de infraestrutura cluster (namespace,
   HPA, `app-service-external.yaml`). O módulo `api-gateway` faz mais sentido
   aqui do que junto do Lambda (repo 1) por ser roteamento de borda do
   cluster; ele passaria a consumir `lambda_invoke_arn`/`lambda_function_name`
   via `terraform_remote_state`/SSM Parameter Store em vez de referência direta
   ao módulo `auth_lambda`. Pipeline roda `terraform fmt/validate/plan` em PR e
   `apply` automático ao mergear em branches de homologação/produção (hoje é
   manual via `workflow_dispatch` — precisa virar `push`/`on: pull_request`
   com merge em branch protegida).
3. **`oficina-infra-db`** — hoje `infra/terraform/modules/postgres-db`. Mesmo
   modelo de pipeline do item 2, isolado por ser um domínio de mudança mais
   sensível (dados) e com blast radius diferente do cluster.
4. **`oficina-app`** — o repositório atual (código Spring Boot, `Dockerfile`,
   `k8s/base/app-*.yaml` + overlays específicos da app, `docker-compose.yml`
   para dev local). Pipeline: testes (`./mvnw clean test`) → build/push de imagem
   → deploy automático da app nos overlays `hml`/`prod` ao mergear nas
   respectivas branches.

Passos executados:
1. `oficina-auth-lambda` ← `lambda-auth-cpf/*` (achatado para a raiz do novo
   repo) + `infra/terraform/modules/auth-lambda` + novo
   `infra/terraform/environments/dev` próprio (antes esse ambiente era
   compartilhado com os outros módulos) + `.github/workflows/{ci,deploy-infra}.yml`
   + `README.md` novo. 14 testes rodados com sucesso na nova localização
   (`mvn clean test`, standalone).
2. `oficina-infra-k8s` ← `infra/terraform/modules/{eks-cluster,api-gateway}`
   + `k8s/observability/` + `k8s/external-secrets/` +
   `scripts/install_observability_stack.sh` + novo
   `infra/terraform/environments/dev` (só `eks_cluster`+`api_gateway`;
   `lambda_function_name`/`lambda_invoke_arn` viraram variáveis de entrada
   simples em vez de referência a `module.auth_lambda`) +
   `.github/workflows/{plan-infra,deploy-infra}.yml` + `README.md` novo.
3. `oficina-infra-db` ← `infra/terraform/modules/postgres-db` + novo
   `infra/terraform/environments/dev` (`vpc_id`/`subnet_ids` viraram
   variáveis de entrada em vez de `module.eks_cluster.*`) +
   `.github/workflows/{plan-infra,deploy-infra}.yml` + `README.md` novo.
4. O repo atual (`oficina`) virou `oficina-app`: removidos `infra/`,
   `lambda-auth-cpf/`, `k8s/observability/`, `k8s/external-secrets/`,
   `scripts/install_observability_stack.sh`. Reescritos
   `scripts/{render_k8s_overlay.sh,deploy_k8s_overlay.sh,ci_deploy.sh}` para
   receber `DB_ENDPOINT`/`DB_PORT`/`DB_NAME`/`DB_USERNAME` via variável de
   ambiente em vez de rodar `terraform output` localmente. Workflows
   `deploy-infra.yml`/`plan-infra.yml` (que rodavam Terraform) substituídos
   por `deploy-app.yml`/`plan-app.yml` (só build+deploy de k8s, sem
   Terraform). `documentacao/infraestrutura.md`, `README.md` e `k8s/README.md`
   atualizados para a nova topologia.

Cada repo tem `README.md` com: propósito, stack, passos de execução/deploy,
diagrama de arquitetura (Mermaid, validado com `mermaid-cli`) específico
daquele repo, e link/nota sobre Swagger/Postman (só faz sentido no
`oficina-app`, que expõe as APIs — já presente no `README.md` original).

Ainda **não feito** (depende de os repositórios existirem no GitHub, o que
não aconteceu nesta rodada por decisão do usuário — só preparação local):
- Criar os 3 novos repositórios no GitHub e dar push (histórico novo, sem
  `git filter-repo`).
- Regras de proteção: branch `main` protegida contra commit direto, PR
  obrigatório para merge, em cada um dos 4 repositórios.
- Trocar os workflows de `workflow_dispatch` para `push`/`on: pull_request`
  em branches de homologação/produção, para deploy automático de verdade
  (hoje todo deploy ainda é `workflow_dispatch` manual em todos os 4 repos).
- Configurar os GitHub Secrets/Environments de cada repositório (listados no
  README de cada um).

## 6. Documentação arquitetural

Tudo sob `documentacao/`, coerente com os arquivos já existentes:

1. `documentacao/diagrama_componentes.md` (ou `.png`/`.drawio`): visão de nuvem
   completa — API Gateway, Lambda de auth, EKS (app), RDS, ferramenta de
   monitoramento, os 4 repositórios e seus pipelines.
2. `documentacao/diagrama_sequencia_auth.md` + `documentacao/diagrama_sequencia_abertura_os.md`:
   fluxo de autenticação por CPF (cliente → API Gateway → Lambda → endpoint
   interno de status → JWT) e fluxo de abertura de OS (cliente autenticado →
   API Gateway → app → `OrdemServicoService.abrirOrdemServico` → persistência).
3. `documentacao/rfcs/` — um arquivo por decisão técnica relevante, ex.:
   - `0001-escolha-da-nuvem.md`
   - `0002-escolha-do-banco-gerenciado.md`
   - `0003-estrategia-de-autenticacao.md` (por que CPF + Lambda + API Gateway,
     alternativas descartadas)
4. `documentacao/adrs/` — decisões arquiteturais permanentes, ex.:
   - `0001-padrao-de-comunicacao.md` (REST síncrono vs. eventos, por que síncrono
     hoje)
   - `0002-uso-de-hpa.md` (justificando `k8s/base/hpa.yaml` já existente)
   - `0003-split-em-quatro-repositorios.md`
5. Atualizar `documentacao/modelo_de_dados.md` com: justificativa formal da
   escolha do Postgres gerenciado (RDS), diagrama ER atualizado (incluindo o novo
   `StatusCliente`), e explicação dos relacionamentos entre os módulos
   (`cliente`, `veiculo`, `ordemServico`, `peca`, `pagamento`).

## 7. Ordem sugerida de execução

A ordem importa porque autenticação por CPF e observabilidade dependem de mudança
de domínio primeiro, e o split de repositórios é mais barato de fazer **depois**
que o código estabilizar (evita repetir migração de histórico):

1. Domínio: `StatusCliente` + endpoint interno de consulta (seção 1).
2. Lambda de autenticação por CPF, ainda testável localmente contra o app atual
   (seção 2).
3. API Gateway na frente do Lambda e da app (seção 3).
4. Observabilidade — instrumentação, logs, dashboards, alertas (seção 4).
5. Documentação arquitetural incremental (seção 6) — ir escrevendo RFC/ADR *no
   momento de cada decisão*, não tudo no final.
6. Split em 4 repositórios + CI/CD + proteção de branch (seção 5) — feito por
   último, empacotando o que já está validado.

## 8. Decisões confirmadas (2026-09-14)

- **Nuvem**: AWS — mantém consistência com o Terraform existente
  (EKS/RDS/Secrets Manager).
- **Runtime do Lambda**: Java. Reaproveita a lógica de `shared/util/CpfValidator`
  diretamente (mesma linguagem), ao custo de cold start maior — aceitável dado
  que autenticação não é um caminho de latência ultra-crítica aqui.
- **Observabilidade**: Prometheus + Grafana no lugar de Datadog/New Relic
  (seção 4 já atualizada para refletir isso — Micrometer + endpoint
  `/actuator/prometheus`, scrape via Prometheus Operator/kube-prometheus-stack no
  cluster, dashboards no Grafana).
- **Split em 4 repositórios**: feito por último (seção 5), com confirmação
  explícita do usuário imediatamente antes de executar a operação — é
  potencialmente destrutiva/irreversível sobre o histórico do Git.

## 9. Progresso

- [x] Seção 1 (domínio): `StatusCliente`, `Cliente.status`, `ClienteService`
  (`consultarStatusPorCpf`, `inativar`, `reativar`), endpoint interno
  `GET /internal/clientes/{cpf}/status` protegido por `InternalApiKeyFilter`
  (header `X-Internal-Api-Key`, propriedade `app.internal.api-key`), endpoints
  `PATCH /clientes/{id}/inativar` e `/reativar`.
- [x] Suporte a dois tipos de principal no JWT: `TipoPrincipal` (`USUARIO`
  default/legado, `CLIENTE` novo), `JwtService.extrairTipo`,
  `ClienteUserDetailsService` (carrega por CPF, exige `isAtivo()`),
  `JwtAuthFilter` roteando para o `UserDetailsService` correto e absorvendo
  `UsernameNotFoundException` (token com assinatura válida mas titular
  inativado/inexistente não autentica, sem 500).
- [x] Seção 2 (Lambda Java de autenticação por CPF): módulo Maven independente
  `lambda-auth-cpf/` (`AuthCpfHandler`, `CpfValidator` portado, `JwtIssuer`
  compatível com `JwtService`, `ClienteStatusPort`/`HttpClienteStatusPort`).
  Empacota como jar shaded (`mvn package`) usado diretamente como pacote de
  deploy do Lambda.
- [x] Terraform do Lambda: `infra/terraform/modules/auth-lambda` (IAM role,
  `aws_lambda_function` apenas — sem API Gateway, ver abaixo).
- [x] Seção 3 (API Gateway): `infra/terraform/modules/api-gateway` (HTTP API
  única, rota `POST /auth/cpf` → Lambda, `ANY /{proxy+}` e `ANY /` → proxy
  HTTP para a aplicação); `k8s/base/app-service-external.yaml` (novo `Service`
  `oficina-app-lb`, `type: LoadBalancer`) resolve o gap "app só tinha
  `ClusterIP`". Wiring em `environments/dev/main.tf`
  (`module.auth_lambda` + `module.api_gateway`, variável `app_public_url`).
  **Não validado com `terraform validate`/`kubectl kustomize`** (CLIs
  indisponíveis neste ambiente de execução) — revisar antes do primeiro
  `apply`/`kubectl apply`. Trade-offs documentados na seção 3 (sem JWT
  authorizer no edge; `/internal/**` alcançável via Gateway, mitigado por
  API key).
- [x] Seção 4 (Prometheus/Grafana): `micrometer-registry-prometheus` +
  `/actuator/prometheus`; `logback-spring.xml` (JSON em docker/k8s, texto
  legível nos demais perfis) + `CorrelationIdFilter` (MDC `correlationId`,
  header `X-Correlation-Id`); métricas de negócio
  (`oficina_ordens_servico_abertas_total`,
  `oficina_ordens_servico_tempo_no_status_seconds{status}`,
  `oficina_notificacoes_falhas_total{canal}`) validadas em teste end-to-end
  real (login → abrir OS → avançar status → conferir métricas); stack
  Prometheus/Grafana via Helm em `k8s/observability/` + `scripts/install_observability_stack.sh`
  (ServiceMonitor, PrometheusRule com 3 alertas, dashboard Grafana com 7
  painéis cobrindo os itens pedidos no desafio).
- [x] Seção 6 (documentação arquitetural): `documentacao/diagrama_componentes.md`
  (visão de nuvem completa + mapeamento para os 4 repositórios-alvo),
  `documentacao/diagrama_sequencia_auth.md`,
  `documentacao/diagrama_sequencia_abertura_os.md` (diagramas Mermaid,
  renderização validada com `@mermaid-js/mermaid-cli` — os 4 diagramas do
  projeto, incluindo o ER, geraram SVG sem erro de parse);
  `documentacao/rfcs/000{1,2,3}-*.md` (nuvem, banco, estratégia de
  autenticação); `documentacao/adrs/000{1,2,3}-*.md` (padrão de comunicação,
  HPA, timing do split); `documentacao/modelo_de_dados.md` atualizado com
  `Cliente.status`/`OrdemServico.status_desde`, diagrama ER em Mermaid,
  justificativa formal do banco e explicação relacionamento a relacionamento.
- [x] Seção 5 (split em 4 repositórios) — executado (2026-09-15), sem
  histórico preservado e sem push/criação no GitHub (decisão do usuário; ver
  ADR 0003). Repos preparados como diretórios irmãos de `oficina`:
  `oficina-auth-lambda`, `oficina-infra-k8s`, `oficina-infra-db`, e o próprio
  `oficina` virou `oficina-app`. Cada um com `git init` + 1 commit inicial.

### Testes

- Aplicação principal (`oficina-app`): 203 testes, 0 falhas (`./mvnw test`).
- `oficina-auth-lambda`: 14 testes, 0 falhas, rodados **na nova localização**
  standalone (`mvn clean test`, fora do monorepo).
- Terraform (nos 3 repos de infra) e os manifestos k8s foram revisados
  manualmente, mas **não** executados contra `terraform`/`kubectl`/`helm`
  reais nesta sessão (CLIs não instaladas no ambiente de execução).
- Dashboard Grafana e regras de alerta validados apenas como JSON/YAML
  bem-formados (parseados com sucesso), não renderizados em um Grafana real.
- Todos os diagramas Mermaid do projeto (incluindo os novos READMEs dos 3
  repositórios extraídos) renderizados com `@mermaid-js/mermaid-cli` sem
  erro de parse.

### Bug real encontrado e corrigido nesta rodada

Adicionar `ClienteUserDetailsService` (seção 1) criou um SEGUNDO bean
`UserDetailsService` no contexto. Isso faz o autoconfig do Spring Security
desistir de montar um `DaoAuthenticationProvider` automaticamente, e o bean
`AuthenticationManager` exposto em `SecurityConfig` passa a ter **si mesmo**
como parent — `AuthService.autenticar` (login de staff) entrava em
`StackOverflowError` a cada chamada. **Nenhum teste existente pegou isso**
porque todos os testes de auth usam `AuthenticationManager` mockado; só foi
descoberto rodando a aplicação de verdade e testando `/auth/login` via curl.
Corrigido publicando um `AuthenticationProvider` (`DaoAuthenticationProvider`)
explícito escopado só a `UserDetailsServiceImpl`; `ClienteUserDetailsService`
continua sendo usado diretamente pelo `JwtAuthFilter`, nunca através do
`AuthenticationManager` global. Teste de regressão adicionado:
`AuthenticationManagerWiringTest` (autentica com o `AuthenticationManager`
real, não mockado). Lição: qualquer mudança em `SecurityConfig`/beans de
`UserDetailsService` daqui pra frente precisa ser validada com um teste que
resolve o `AuthenticationManager` real do contexto, não só mocks.

### Correções feitas nesta rodada (achadas ao revisar o próprio trabalho)

- `k8s/base/secret.yaml` e `k8s/overlays/{hml,prod}/externalsecret.yaml` não
  tinham `APP_INTERNAL_API_KEY` — a app teria falhado ao subir em hml/prod
  (`app.internal.api-key=${APP_INTERNAL_API_KEY}` sem default). Corrigido;
  `/oficina/<ambiente>/app` no Secrets Manager agora precisa também da
  propriedade `APP_INTERNAL_API_KEY` (documentado em `infraestrutura.md`).
- `plan-infra.yml`/`deploy-infra.yml` rodavam `terraform plan/apply` sem
  nunca ter construído `lambda-auth-cpf/target/oficina-auth-lambda.jar` nem
  fornecido `jwt_secret`/`internal_api_key` — teria quebrado ambos os
  workflows. Corrigido: passo de build do jar antes do Terraform, novos
  secrets `JWT_SECRET`/`APP_INTERNAL_API_KEY` wired como
  `TF_VAR_jwt_secret`/`TF_VAR_internal_api_key`, e `scripts/ci_deploy.sh`
  valida a presença deles cedo. `scripts/setup_github_secrets.sh` atualizado.
- `app_public_url` ganhou um default placeholder (`https://example.invalid`)
  porque seu valor real só existe depois do primeiro `kubectl apply -k
  k8s/overlays/<env>` (o Load Balancer `oficina-app-lb` ainda não existe no
  primeiro `terraform apply`) — sem o default, `apply` falharia sempre no
  primeiro run. Precisa de um segundo `terraform apply -var
  app_public_url=...` depois do k8s deploy, conforme documentado em
  `infraestrutura.md`.
