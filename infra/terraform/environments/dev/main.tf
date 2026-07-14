terraform {
  required_version = ">= 1.6.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }
}

provider "aws" {
  region = var.aws_region
}

variable "project_name" {
  description = "Project name used as a naming prefix."
  type        = string
  default     = "oficina"
}

variable "environment" {
  description = "Infrastructure environment name."
  type        = string
  default     = "dev"
}

variable "aws_region" {
  description = "AWS region for the dev environment."
  type        = string
  default     = "us-east-1"
}

variable "db_username" {
  description = "Database admin username."
  type        = string
  default     = "postgres"
}

variable "db_password" {
  description = "Database admin password."
  type        = string
  sensitive   = true
}

locals {
  name_prefix = "${var.project_name}-${var.environment}"
  tags = {
    Project     = var.project_name
    Environment = var.environment
    ManagedBy   = "terraform"
  }
}

module "eks_cluster" {
  source = "../../modules/eks-cluster"

  name_prefix          = local.name_prefix
  availability_zones   = ["${var.aws_region}a", "${var.aws_region}b"]
  public_subnet_cidrs  = ["10.0.1.0/24", "10.0.2.0/24"]
  node_group_min_size  = 1
  node_group_max_size  = 3
  node_group_desired_size = 2
  tags                 = local.tags
}

module "postgres_db" {
  source = "../../modules/postgres-db"

  name_prefix         = local.name_prefix
  vpc_id              = module.eks_cluster.vpc_id
  subnet_ids          = module.eks_cluster.subnet_ids
  username            = var.db_username
  password            = var.db_password
  allowed_cidr_blocks = ["10.0.0.0/16"]
  tags                = local.tags
}

output "cluster_name" {
  value       = module.eks_cluster.cluster_name
  description = "Provisioned EKS cluster name."
}

output "cluster_endpoint" {
  value       = module.eks_cluster.cluster_endpoint
  description = "Provisioned EKS cluster endpoint."
}

output "db_endpoint" {
  value       = module.postgres_db.db_endpoint
  description = "Provisioned PostgreSQL endpoint."
}

output "db_port" {
  value       = module.postgres_db.db_port
  description = "Provisioned PostgreSQL port."
}

output "db_name" {
  value       = module.postgres_db.db_name
  description = "Provisioned PostgreSQL database name."
}

output "db_username" {
  value       = module.postgres_db.db_username
  description = "Provisioned PostgreSQL username."
}