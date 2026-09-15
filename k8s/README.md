Kubernetes manifests for Oficina (oficina-app)

Base manifests included:
- namespace
- configmap
- secret
- application deployment and service (`oficina-app`, `ClusterIP`)
- public load balancer service (`oficina-app-lb`)
- postgres deployment, service, and pvc (dev overlay only)
- horizontal pod autoscaler
- kustomize base and overlays for `dev`, `demo`, `hml`, and `prod`

Apply with Kustomize:

```bash
kubectl apply -k k8s/overlays/dev
```

Notes:
- The `oficina:latest` image is intended for local clusters such as `kind` or `k3d`.
- The `hml` and `prod` overlays already expect a registry-hosted image name and tag.
- The HPA requires `metrics-server` installed in the cluster.
- The included PostgreSQL manifest is suitable for local or non-critical environments. For production, prefer the managed database provisioned by the `oficina-infra-db` repository.
- The `dev` overlay keeps PostgreSQL in the cluster. The `demo`, `hml` and `prod` overlays remove it and expect a managed database endpoint (from `oficina-infra-db`).
- The `hml` and `prod` overlays use External Secrets and remove inline secret patching.
- The `demo` overlay is a one-off middle ground: real managed database like `hml`/`prod`, but a plain `Secret` patch (`DB_PASSWORD` token) instead of External Secrets — for running once against real AWS without installing the External Secrets Operator / provisioning AWS Secrets Manager entries. Not meant for repeated/production use.

## Cross-repository dependencies (after the 4-repository split)

This repository owns **no Terraform state**. The cluster itself, the API
Gateway, and the `ClusterSecretStore` used by External Secrets are
provisioned by [`oficina-infra-k8s`](../documentacao/plano_implementacao.md);
the managed database is provisioned by `oficina-infra-db`. Before deploying
here:

1. Apply `oficina-infra-k8s` (provisions the EKS cluster) and, once the
   cluster exists, its `ClusterSecretStore`
   (`kubectl apply -f k8s/external-secrets/clustersecretstore-aws.yaml` — that
   file now lives in the `oficina-infra-k8s` repo, not here).
2. Apply `oficina-infra-db`, using `vpc_id`/`subnet_ids` output by
   `oficina-infra-k8s`.
3. Collect `db_endpoint`, `db_port`, `db_name`, `db_username` from
   `oficina-infra-db`'s Terraform outputs and pass them to the scripts below
   as `DB_ENDPOINT`/`DB_PORT`/`DB_NAME`/`DB_USERNAME`.
4. Deploy this repo's overlay (`hml`/`prod`) with those values.
5. Once `oficina-app-lb` gets a hostname, publish it back to
   `oficina-infra-k8s` (`app_public_url`) so the API Gateway can proxy to it,
   and to `oficina-auth-lambda` (`app_public_url`) so the Lambda can reach
   `/internal/clientes/{cpf}/status`.

Automated rendering for `hml` and `prod` overlays:

```bash
DB_ENDPOINT="..." DB_PORT="5432" DB_NAME="oficina_db" DB_USERNAME="postgres" \
scripts/render_k8s_overlay.sh hml
```

When using External Secrets (default), the JWT secret and internal API key
are resolved in-cluster and only the database connection values are
rendered into the ConfigMap.

Optional SMTP credentials for production:

```bash
DB_ENDPOINT="..." DB_PORT="5432" DB_NAME="oficina_db" DB_USERNAME="postgres" \
SPRING_MAIL_USERNAME="..." SPRING_MAIL_PASSWORD="..." \
scripts/render_k8s_overlay.sh prod
```

After rendering, apply the generated directory shown by the script with:

```bash
kubectl apply -k <rendered-overlay-path>
```

Automated render + apply + rollout + smoke test:

```bash
DB_ENDPOINT="..." DB_PORT="5432" DB_NAME="oficina_db" DB_USERNAME="postgres" \
scripts/deploy_k8s_overlay.sh hml
```

Optional flags as environment variables:
- `WAIT_FOR_ROLLOUT=false` to skip rollout waiting
- `RUN_SMOKE_TEST=false` to skip post-deploy smoke check
- `NAMESPACE=<ns>` to target another namespace
- `ROLLBACK_ON_SMOKE_FAIL=false` to disable automatic rollout undo on smoke-test failure
- `AUTH_SMOKE_ENABLED=true` to enable authenticated smoke check
- `AUTH_SMOKE_LOGIN_URL`, `AUTH_SMOKE_TARGET_URL`, `AUTH_SMOKE_LOGIN`, `AUTH_SMOKE_PASSWORD` to customize authenticated smoke behavior
- `DB_SMOKE_ENABLED=true` to enable database health smoke check via actuator
- `DB_SMOKE_URL` to override database smoke endpoint

Safety gates applied in deploy scripts:
- rendered overlay validation via `kubectl kustomize`
- optional automatic rollback (`kubectl rollout undo`) when smoke test fails

CI-oriented flow (no Terraform involved — this repo only renders/applies k8s manifests):

```bash
DB_ENDPOINT="..." DB_PORT="5432" DB_NAME="oficina_db" DB_USERNAME="postgres" \
scripts/ci_deploy.sh hml
```

Default CI mode is `USE_EXTERNAL_SECRETS=true`.

GitHub Actions workflows:
- `.github/workflows/plan-app.yml` — renders and validates (`kubectl kustomize`)
  an overlay without applying it; uploads the rendered overlay as an artifact.
- `.github/workflows/deploy-app.yml` — builds/pushes the Docker image and
  deploys the target overlay end to end.
