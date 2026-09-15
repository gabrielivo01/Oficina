# Diagrama de Componentes

Visão de nuvem completa do sistema: API Gateway, function serverless de
autenticação, cluster Kubernetes com a aplicação principal, banco gerenciado,
e a stack de observabilidade. Ver `documentacao/plano_implementacao.md` para
o detalhamento de cada peça e `documentacao/infraestrutura.md` para os passos
de deploy.

```mermaid
graph TB
    cliente["Cliente (app/frontend)"]

    subgraph aws["AWS"]
        subgraph gw["API Gateway (HTTP API)"]
            rotaAuth["POST /auth/cpf"]
            rotaProxy["ANY /{proxy+}, ANY /"]
        end

        lambda["Lambda: oficina-auth-lambda\n(AuthCpfHandler, Java 21)"]

        subgraph eks["Cluster EKS (namespace oficina)"]
            appLB["Service oficina-app-lb\n(LoadBalancer)"]
            appClusterIP["Service oficina-app\n(ClusterIP)"]
            appPods["Deployment oficina-app\n(2-5 réplicas via HPA)"]
            appLB --> appPods
            appClusterIP --> appPods
        end

        rds[("RDS PostgreSQL")]

        secretsmgr["AWS Secrets Manager\n/oficina/<ambiente>/app (JWT_SECRET, APP_INTERNAL_API_KEY)\n/oficina/<ambiente>/database\n/oficina/<ambiente>/mail"]

        subgraph monitoring["Namespace monitoring (kube-prometheus-stack)"]
            prometheus["Prometheus\n(scrape /actuator/prometheus)"]
            grafana["Grafana\n(dashboard oficina-overview)"]
            alertmanager["Alertmanager\n(3 regras: falha notificação,\n5xx alto, app down)"]
            prometheus --> grafana
            prometheus --> alertmanager
        end
    end

    cliente -->|"POST /auth/cpf {cpf}"| rotaAuth
    cliente -->|"demais rotas + Bearer token"| rotaProxy
    rotaAuth -->|AWS_PROXY| lambda
    rotaProxy -->|HTTP_PROXY| appLB
    lambda -->|"GET /internal/clientes/{cpf}/status\nX-Internal-Api-Key"| appLB
    lambda -.->|"JWT_SECRET, APP_INTERNAL_API_KEY"| secretsmgr
    appPods -.->|"JWT_SECRET, APP_INTERNAL_API_KEY,\nDB creds, mail creds"| secretsmgr
    appPods --> rds
    prometheus -->|"scrape a cada 15s"| appClusterIP

    style lambda fill:#f9e79f,stroke:#b7950b
    style rds fill:#d6eaf8,stroke:#2874a6
    style secretsmgr fill:#f5b7b1,stroke:#943126
```

## Mapeamento para os 4 repositórios (seção 5 do plano)

| Componente no diagrama | Repositório-alvo |
|---|---|
| `Lambda: oficina-auth-lambda` | 1. `oficina-auth-lambda` |
| `API Gateway`, EKS (Terraform: cluster, HPA, Service externo) | 2. `oficina-infra-k8s` |
| `RDS PostgreSQL` (Terraform) | 3. `oficina-infra-db` |
| `Deployment oficina-app`, `Service oficina-app`/`oficina-app-lb`, código Spring Boot | 4. `oficina-app` |

Cada um desses repositórios terá seu próprio pipeline de CI/CD (build + testes
+ deploy automático), conforme detalhado na seção 5 do plano de implementação.
