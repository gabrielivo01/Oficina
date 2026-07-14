Infraestrutura e conteinerização

Escopo já iniciado nesta etapa:
- externalização de configuração para execução local, Docker e Kubernetes
- inclusão de actuator para health checks
- execução local com `docker-compose` incluindo aplicação, PostgreSQL e MailHog
- manifestos Kubernetes base para aplicação, banco, ConfigMap, Secret, Service e HPA
- overlays Kubernetes para `dev`, `hml` e `prod`
- módulos Terraform para AWS EKS e RDS PostgreSQL

Arquivos criados ou atualizados:
- `Dockerfile`
- `docker-compose.yml`
- `src/main/resources/application.properties`
- `src/main/resources/application-docker.properties`
- `src/main/resources/application-k8s.properties`
- `k8s/base/*.yaml`
- `k8s/overlays/*`
- `infra/terraform/environments/dev/main.tf`
- `infra/terraform/environments/dev/terraform.tfvars.example`
- `infra/terraform/modules/eks-cluster/*`
- `infra/terraform/modules/postgres-db/*`
- `infra/terraform/README.md`

Como executar localmente com Docker Compose:

```bash
docker compose up --build
```

URLs esperadas:
- aplicação: `http://localhost:8080`
- swagger: `http://localhost:8080/swagger-ui.html`
- mailhog: `http://localhost:8025`

Como aplicar no Kubernetes:

```bash
kubectl apply -k k8s/overlays/dev
```

Estratégia por ambiente no Kubernetes:
- `dev`: usa PostgreSQL in-cluster para facilitar teste local
- `hml`: usa imagem de registry e remove o PostgreSQL in-cluster
- `prod`: usa imagem de registry, remove o PostgreSQL in-cluster e espera segredos vindos de pipeline ou secret manager

Integração com AWS Secrets Manager (hml/prod):
- `k8s/external-secrets/clustersecretstore-aws.yaml` define o `ClusterSecretStore`
- overlays `hml` e `prod` usam `ExternalSecret` para gerar o `oficina-secret`
- chaves esperadas no Secrets Manager:
	- `/oficina/<ambiente>/app` propriedade `JWT_SECRET`
	- `/oficina/<ambiente>/database` propriedade `PASSWORD`
	- `/oficina/<ambiente>/mail` propriedades `USERNAME` e `PASSWORD`

Como aplicar a infraestrutura AWS com Terraform:

```bash
cd infra/terraform/environments/dev
cp terraform.tfvars.example terraform.tfvars
terraform init
terraform plan -var-file=terraform.tfvars
terraform apply -var-file=terraform.tfvars
```

Integração Terraform -> Kubernetes:
1. obter outputs `db_endpoint`, `db_port`, `db_name` e `db_username`
2. atualizar o overlay do ambiente com a JDBC URL correta
3. garantir que os segredos existam no AWS Secrets Manager para o `ExternalSecret`
4. aplicar `kubectl apply -k k8s/overlays/<ambiente>`

Automação ponta a ponta (render + apply + smoke test):

```bash
DB_PASSWORD="..." \
scripts/deploy_k8s_overlay.sh hml infra/terraform/environments/dev
```

Scripts disponíveis:
- `scripts/render_k8s_overlay.sh`: renderiza overlay com outputs do Terraform
- `scripts/deploy_k8s_overlay.sh`: renderiza, aplica no cluster, aguarda rollout e executa smoke test
- `scripts/ci_deploy.sh`: executa `terraform init/plan` (e opcionalmente `apply`) e depois chama o deploy Kubernetes

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

Gates adicionados no CI Terraform:
- `terraform fmt -check`
- `terraform validate`
- `terraform plan`

Workflow CI/CD adicionado:
- `.github/workflows/deploy-infra.yml` com execução manual (`workflow_dispatch`) para `hml` e `prod`
- usa segredos para AWS e banco (JWT vem do AWS Secrets Manager no modo padrão)
- executa testes, planeja/aplica Terraform e faz deploy no cluster
- usa `environment` dinâmico (`hml` ou `prod`) para permitir aprovação obrigatória no ambiente de produção

Workflow de revisão sem deploy:
- `.github/workflows/plan-infra.yml`
- executa testes, `terraform fmt/validate/plan` e renderiza overlay do ambiente
- não executa apply nem deploy em cluster
- inclui scanner de segurança IaC com `tfsec` e `checkov`
- publica artifacts com `tfplan`, `tfplan.txt` e overlay renderizado para revisão

Rastreabilidade de deploy:
- `.github/workflows/deploy-infra.yml` publica artifact do overlay efetivamente renderizado/aplicado
- permite auditoria de configuração entre execuções de pipeline

Configuração recomendada no GitHub:
1. criar environments `hml` e `prod`
2. no environment `prod`, habilitar required reviewers para aprovação manual antes do job
3. cadastrar os segredos de smoke autenticado quando for habilitar `AUTH_SMOKE_ENABLED=true`

Próximos passos recomendados:
1. publicar a imagem em registry e ajustar os overlays com a tag do pipeline
2. integrar os outputs do Terraform com os Secrets e ConfigMaps do ambiente
3. endurecer a rede e a estratégia de banco para produção
4. validar `kubectl apply -k` e `terraform plan/apply` em ambiente com tooling instalado