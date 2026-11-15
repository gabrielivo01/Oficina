# Progresso — Fase 4 (Microsserviços + Saga)

Registro de execução de `documentacao/plano_fase4.md`. Lido no início de cada fase; mantido conciso.

## Status geral
- **Fase atual:** 1 — Plataforma local (`oficina-infra-k8s`)
- **Última fase concluída:** 0 — Padronização e ADRs
- **Atualizado em:** 2026-11-15 19:05

## Fases concluídas

### Fase 0 — Padronização e ADRs (repo `oficina`)
- **Entregue (antes desta execução automatizada):** ArchUnit com FreezingArchRule (`26c68c0`), guia do projeto + regras de domínio (`4e73905`),
  Spotless palantir (`e4cf77f`, `ffdead9`), JaCoCo com limite + `verify` no CI e no pre-push (`d0e4c2e`),
  hooks de formatação e compilação (`9cc55ac`), skill `novo-caso-de-uso` (`75e17ff`), subagent `revisor` (`408f752`),
  ADRs 0004–0009 (`9f27d44`), hook `commit-msg` (`0dc906b`), Prometheus/Grafana no compose (`834d4e8`),
  `APP_INTERNAL_API_KEY` no compose (`8b84232`), plano versionado (`1e04ad5`).
- **Validação:** `./mvnw -q verify` verde em 2026-11-15 (sem `[ERROR]`); ADRs 0004–0009 presentes.
- **Tag:** `fase-0` (oficina).

## Decisões autônomas
| Decisão | Motivo | Fase |
|---|---|---|
| Pasta de trabalho real é `~/git/oficina-project` (o briefing citava `~/oficina-workspace`). Todos os repos ficam lá, como irmãos de `oficina`. | É onde os repos existem; o prompt do usuário define essa pasta. | 0 |
| As instruções do cabeçalho do plano ("uma fase por sessão", "espere aprovação", "pare e pergunte") são substituídas pela execução autônoma pedida pelo usuário; conflitos com o código real são resolvidos pela opção mais alinhada às seções 1 e 2 e registrados aqui. | Instrução explícita do usuário para esta execução. | 0 |
| `actionlint`, `shellcheck` e `kubeconform` não estão instalados no host; serão executados via imagens Docker oficiais (`rhysd/actionlint`, `koalaman/shellcheck`, `ghcr.io/yannh/kubeconform`). | Não alterar arquivos fora da pasta de trabalho (sem instalar binários no host). | 0 |

## Pendências para o usuário
- (nenhuma até aqui)

## Problemas conhecidos / dívidas
- Docker do host (WSL2) tem ~3,9 GB de memória: o cluster kind com kube-prometheus-stack + RabbitMQ + Jaeger + 3 serviços e bancos pode ficar sem memória. Se necessário, aumentar a memória do WSL (`.wslconfig`, `memory=8GB`).
