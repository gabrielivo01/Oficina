# RFC 0001: Escolha do provedor de nuvem

- Status: Aceita
- Data: 2026-09-14

## Contexto

O desafio exige API Gateway, function serverless, banco gerenciado e cluster
Kubernetes com escalabilidade, tudo provisionado via Terraform, com liberdade
de escolha de provedor. No momento desta decisão, o projeto já tinha Terraform
para EKS (`infra/terraform/modules/eks-cluster`) e RDS PostgreSQL
(`infra/terraform/modules/postgres-db`) escrito para AWS, além de manifests
Kubernetes com `ExternalSecret` apontando para AWS Secrets Manager
(`k8s/external-secrets/clustersecretstore-aws.yaml`).

## Decisão

Manter **AWS** como único provedor de nuvem para todos os componentes novos
(Lambda de autenticação, API Gateway, stack de observabilidade).

## Alternativas consideradas

- **GCP** (Cloud Functions + API Gateway + Cloud SQL + GKE): descartado —
  exigiria reescrever os dois módulos Terraform já existentes e o mecanismo de
  segredos, sem ganho correspondente.
- **Azure** (Functions + API Management + Azure Database + AKS): mesmo
  problema de retrabalho, descartado pelo mesmo motivo.
- **Multi-cloud** (ex.: Lambda na AWS, cluster em outro provedor): descartado
  por complexidade de rede/observabilidade cross-cloud sem nenhum requisito
  do desafio que justifique isso.

## Consequências

- Todo o Terraform novo (`infra/terraform/modules/auth-lambda`,
  `infra/terraform/modules/api-gateway`) usa o provider `hashicorp/aws`,
  consistente com os módulos existentes.
- O Lambda usa o runtime `java21` da AWS Lambda e é empacotado como jar
  shaded, sem camada de abstração cross-cloud (ex.: Serverless Framework)
  porque não há necessidade de portabilidade agora.
- Fica mais barato herdar os padrões já estabelecidos (Secrets Manager,
  tagging via `local.tags`, convenção de nomes `${project}-${environment}-*`)
  em vez de reinventá-los para um provedor diferente.
