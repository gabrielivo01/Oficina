# ADR 0003: Split em quatro repositórios (timing e escopo)

- Status: Aceita, executada
- Data da decisão: 2026-09-14
- Data da execução: 2026-09-15

## Contexto

O desafio exige quatro repositórios separados, cada um com seu próprio
CI/CD: (1) Lambda, (2) infraestrutura Kubernetes, (3) infraestrutura de banco
gerenciado, (4) aplicação principal. Hoje tudo vive em um único monorepo
(`oficina`), incluindo `lambda-auth-cpf/` como módulo Maven independente e
`infra/terraform/modules/{auth-lambda,api-gateway,eks-cluster,postgres-db}`.

## Decisão

Adiar o split para **depois** que domínio, Lambda, API Gateway e
observabilidade estiverem implementados e testados (seção 7 do plano de
implementação) — e executá-lo somente mediante confirmação explícita do
usuário, por ser uma operação potencialmente destrutiva/irreversível sobre o
histórico do Git.

Mapeamento já decidido para quando o split acontecer (ver
`documentacao/plano_implementacao.md`, seção 5):

1. `oficina-auth-lambda` ← `lambda-auth-cpf/`
2. `oficina-infra-k8s` ← `infra/terraform/modules/{eks-cluster,api-gateway}` + manifests de infraestrutura de cluster
3. `oficina-infra-db` ← `infra/terraform/modules/postgres-db`
4. `oficina-app` ← o monorepo atual, com os módulos Terraform extraídos removidos

## Alternativas consideradas

- **Split imediato, antes do código estabilizar**: descartado — cada mudança
  de contrato entre componentes (ex.: a claim `tipo` no JWT, o endpoint
  `/internal/clientes/{cpf}/status`) teria exigido coordenar PRs em múltiplos
  repositórios ao mesmo tempo, multiplicando o custo de iteração durante a
  fase mais instável do desenvolvimento.
- **Nunca dividir (manter monorepo)**: não atende ao requisito explícito do
  desafio de quatro repositórios com CI/CD próprio.

## Consequências

- **Execução real (2026-09-15)**: decidido com o usuário fazer o split sem
  preservar histórico (repositórios novos, `git init` + primeiro commit —
  não `git filter-repo`) e **sem** criar/pushar para o GitHub nesta rodada;
  os 3 novos repositórios (`oficina-auth-lambda`, `oficina-infra-k8s`,
  `oficina-infra-db`) foram preparados localmente, como diretórios irmãos de
  `oficina` (agora `oficina-app`), cada um com seu próprio `.git`.
- `infra/terraform/modules/api-gateway` (agora em `oficina-infra-k8s`) não
  referencia mais `module.auth_lambda` diretamente — `lambda_function_name`
  e `lambda_invoke_arn` viraram variáveis de entrada simples, preenchidas
  manualmente a partir dos outputs do repositório `oficina-auth-lambda` (o
  mesmo padrão manual já usado para `app_public_url`/`vpc_id`/`subnet_ids`
  entre os outros repositórios — nenhum backend remoto compartilhado
  (S3+DynamoDB) foi configurado, por não existir infraestrutura real para
  isso neste momento).
- `oficina-app` (este repositório) perdeu `infra/`, `lambda-auth-cpf/`,
  `k8s/observability/`, `k8s/external-secrets/` e
  `scripts/install_observability_stack.sh` — movidos para os repositórios
  correspondentes. `scripts/{render_k8s_overlay.sh,deploy_k8s_overlay.sh,ci_deploy.sh}`
  foram reescritos para receber `DB_ENDPOINT`/`DB_PORT`/`DB_NAME`/`DB_USERNAME`
  como variáveis de ambiente em vez de rodar `terraform output` localmente
  (esse estado agora vive em outro repositório). Os workflows
  `deploy-infra.yml`/`plan-infra.yml` foram substituídos por
  `deploy-app.yml`/`plan-app.yml`, sem nenhum passo de Terraform.
- **Não executado nesta rodada**: criação dos repositórios no GitHub, push,
  configuração de branch protection/PR obrigatório, e configuração dos
  GitHub Environments/segredos em cada um — tudo isso depende de decisão
  explícita do usuário sobre quando publicar (a extração ficou só local).
