Terraform structure for Oficina infrastructure

Purpose:
- provision a Kubernetes cluster
- provision a PostgreSQL database
- organize environment-specific variables and outputs

Current implementation target:
- AWS EKS for Kubernetes
- AWS RDS PostgreSQL for the database

Suggested workflow:

```bash
cd infra/terraform/environments/dev
terraform init
cp terraform.tfvars.example terraform.tfvars
terraform plan -var-file=terraform.tfvars
terraform apply -var-file=terraform.tfvars
```

Resources created in this version:
- VPC dedicated to the project
- public subnets for the initial cluster footprint
- internet gateway and public routing
- EKS cluster with managed node group
- RDS PostgreSQL instance
- security group and DB subnet group for the database
- outputs for cluster endpoint and database endpoint

Recommended split:
- `modules/cluster`: cluster provisioning
- `modules/database`: database provisioning
- `environments/dev`: environment wiring for a first deployable environment

Notes:
- AWS credentials must be configured in the environment before running Terraform.
- The RDS instance is private by default.
- This environment is intended as a starting point and should be hardened before production use.