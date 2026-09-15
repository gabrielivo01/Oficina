# ADR 0002: Uso de HPA (Horizontal Pod Autoscaler)

- Status: Aceita (documenta decisão pré-existente: `k8s/base/hpa.yaml`)
- Data: 2026-09-14

## Contexto

O desafio pede "Cluster Kubernetes com escalabilidade" como requisito de
infraestrutura obrigatória. O projeto já tinha `k8s/base/hpa.yaml`
provisionado antes desta rodada de trabalho; este ADR formaliza a decisão que
já estava implícita no manifesto.

## Decisão

Usar `HorizontalPodAutoscaler` (`autoscaling/v2`) sobre o `Deployment
oficina-app`, com:

- `minReplicas: 2`, `maxReplicas: 5`
- Métrica de CPU: 70% de utilização média
- Métrica de memória: 75% de utilização média

## Alternativas consideradas

- **Sem autoscaling (réplicas fixas)**: descartado — não atende ao requisito
  explícito de "escalabilidade" do desafio, e deixaria a aplicação vulnerável
  a picos de carga (ex.: múltiplas aberturas de OS simultâneas).
- **KEDA (autoscaling orientado a eventos)**: mais adequado se a aplicação
  processasse filas (SQS, Kafka) — não é o caso aqui (comunicação é
  síncrona via HTTP, ver ADR 0001), então HPA baseado em métricas de
  recurso (CPU/memória) já é suficiente e mais simples de operar.
- **Vertical Pod Autoscaler (VPA)** em vez de HPA: resolve um problema
  diferente (redimensionar requests/limits do pod, não a quantidade de
  réplicas); não substitui a necessidade de escalar horizontalmente sob
  carga. Poderia ser usado em conjunto no futuro, não é uma alternativa ao
  HPA.

## Consequências

- `minReplicas: 2` já garante alguma tolerância a falha de pod mesmo sem
  carga alta (não é só sobre escalar para cima).
- Os *targets* de CPU/memória do HPA (70%/75%) devem ficar coerentes com os
  `resources.requests`/`resources.limits` do `app-deployment.yaml`
  (`250m`/`500m` CPU, `256Mi`/`512Mi` memória) — se algum desses valores
  mudar, revisar o HPA junto.
- Com a métrica `oficina_ordens_servico_tempo_no_status_seconds` e o painel
  de CPU/memória do dashboard Grafana (`k8s/observability/`), agora é
  possível correlacionar picos de latência/tempo de status com o
  comportamento do HPA — algo que não era observável antes desta rodada de
  trabalho (seção 4 do plano de implementação).
