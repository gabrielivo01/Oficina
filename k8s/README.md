Kubernetes manifests for Oficina

Base manifests included:
- namespace
- configmap
- secret
- application deployment and service
- postgres deployment, service, and pvc
- horizontal pod autoscaler
- kustomize base and overlays for `dev`, `hml`, and `prod`

Apply with Kustomize:

```bash
kubectl apply -k k8s/overlays/dev
```

Notes:
- The `oficina:latest` image is intended for local clusters such as `kind` or `k3d`.
- The `hml` and `prod` overlays already expect a registry-hosted image name and tag.
- The HPA requires `metrics-server` installed in the cluster.
- The included PostgreSQL manifest is suitable for local or non-critical environments. For production, prefer a managed database.
- The `dev` overlay keeps PostgreSQL in the cluster. The `hml` and `prod` overlays remove it and expect a managed database endpoint.
- The `hml` and `prod` overlays now use External Secrets and remove inline secret patching.

External Secrets prerequisite:

```bash
kubectl apply -f k8s/external-secrets/clustersecretstore-aws.yaml
```

Using Terraform outputs with Kubernetes:

1. Run Terraform in the target environment and collect `db_endpoint`, `db_port`, `db_name`, and `db_username`.
2. Patch the overlay ConfigMap with the managed database JDBC URL.
3. Provide secrets through External Secrets (AWS Secrets Manager + ClusterSecretStore).
4. Apply the target overlay with `kubectl apply -k`.

Automated rendering for `hml` and `prod` overlays:

```bash
DB_PASSWORD="..." \
scripts/render_k8s_overlay.sh hml infra/terraform/environments/dev
```

When using External Secrets (default), JWT secret is resolved in cluster and only Terraform/database values are rendered.

Optional SMTP credentials for production:

```bash
DB_PASSWORD="..." \
SPRING_MAIL_USERNAME="..." SPRING_MAIL_PASSWORD="..." \
scripts/render_k8s_overlay.sh prod infra/terraform/environments/dev
```

After rendering, apply the generated directory shown by the script with:

```bash
kubectl apply -k <rendered-overlay-path>
```

Automated render + apply + rollout + smoke test:

```bash
DB_PASSWORD="..." \
scripts/deploy_k8s_overlay.sh hml infra/terraform/environments/dev
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

Safety gates now applied in deploy scripts:
- rendered overlay validation via `kubectl kustomize`
- optional automatic rollback (`kubectl rollout undo`) when smoke test fails

CI-oriented flow with Terraform plan/apply integration:

```bash
DB_PASSWORD="..." \
TERRAFORM_AUTO_APPLY=true \
scripts/ci_deploy.sh hml infra/terraform/environments/dev
```

Default CI mode is `USE_EXTERNAL_SECRETS=true`.

Terraform validation gates in CI flow:
- `terraform fmt -check`
- `terraform validate`
- `terraform plan`

Plan-only workflow available:
- `.github/workflows/plan-infra.yml` runs validation and plan without apply/deploy.
- It uploads `tfplan`, textual plan output, and rendered overlay as workflow artifacts.

Deploy workflow traceability:
- `.github/workflows/deploy-infra.yml` uploads the rendered/applied overlay as an artifact.