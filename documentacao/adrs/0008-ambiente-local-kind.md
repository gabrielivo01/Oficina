# ADR 0008: Ambiente 100% local com Docker Compose e kind

- Status: Aceita
- Data: 2026-11-15

## Contexto

A Fase 3 provisionou a aplicação na AWS (EKS, RDS, API Gateway, Lambda) via
Terraform. A Fase 4 multiplica a infraestrutura: três serviços, três bancos
(dois PostgreSQL e um MongoDB), RabbitMQ, OTel Collector, Jaeger e a stack
Prometheus/Grafana. Manter tudo isso na AWS durante o desenvolvimento e no
CI tem custo recorrente, e o desafio exige "deploy automatizado em
Kubernetes" a cada pipeline, não um provedor específico.

## Decisão

- **Custo zero, tudo local nesta fase**:
  - **Docker Compose** para desenvolvimento (plataforma em
    `oficina-infra-k8s/local/`: RabbitMQ, os três bancos, Jaeger, OTel
    Collector, MailHog).
  - **kind** (Kubernetes in Docker) como cluster, tanto na máquina do
    desenvolvedor quanto **no GitHub Actions**: o pipeline de CD de cada
    serviço sobe um kind no runner (`scripts/plataforma_up.sh`), faz o
    deploy dos três serviços com Kustomize e roda um smoke test E2E.
  - Imagens publicadas no GHCR.
- **A infraestrutura AWS da Fase 3 fica como está** (Lambda de CPF, API
  Gateway, Terraform de EKS/RDS): não é apagada nem migrada; é o caminho de
  evolução para produção.

## Alternativas consideradas

- **Continuar no EKS**: ambiente mais próximo de produção, mas com custo
  contínuo (control plane, nós, RDS, NAT) multiplicado por três serviços e
  dependência de credenciais AWS no CI de cada repositório.
- **minikube / k3d**: equivalentes para uso local; kind foi escolhido por
  ser o padrão de fato no GitHub Actions (action oficial, sem VM aninhada) e
  por usar o mesmo Docker já necessário para o Compose.
- **Só Docker Compose, sem Kubernetes**: não cumpre o requisito de deploy
  em Kubernetes.

## Consequências

- O requisito "deploy automatizado em Kubernetes" é cumprido a cada push na
  `main`: o cluster é efêmero, criado e destruído no runner, com validação
  por rollout, health checks e smoke test.
- Sem URL pública: o webhook do Mercado Pago depende de túnel (ngrok /
  Cloudflare Tunnel) na demonstração, com polling agendado como plano B.
- Dívidas da Fase 3 na AWS (Terraform sem backend remoto, EKS público,
  possível bypass do API Gateway) não são tratadas nesta fase; ficam
  registradas no README como dívida.
- Os manifests são escritos com Kustomize (base + overlay `local`), o que
  permite adicionar um overlay `eks` no futuro sem reescrever a base.
