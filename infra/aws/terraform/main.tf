locals {
  tags = {
    Project     = "ReleasePilot"
    Environment = "demo"
    ManagedBy   = "Terraform"
    Owner       = var.owner
  }
}

module "vpc" {
  source  = "terraform-aws-modules/vpc/aws"
  version = "~> 6.0"

  name            = "${var.cluster_name}-vpc"
  cidr            = "10.42.0.0/16"
  azs             = ["${var.aws_region}a", "${var.aws_region}c"]
  public_subnets  = ["10.42.0.0/20", "10.42.16.0/20"]
  private_subnets = ["10.42.128.0/20", "10.42.144.0/20"]

  enable_nat_gateway   = true
  single_nat_gateway   = true
  enable_dns_hostnames = true
  public_subnet_tags   = { "kubernetes.io/role/elb" = 1 }
  private_subnet_tags  = { "kubernetes.io/role/internal-elb" = 1 }
}

module "eks" {
  source  = "terraform-aws-modules/eks/aws"
  version = "~> 21.0"

  name                                     = var.cluster_name
  kubernetes_version                       = "1.34"
  endpoint_public_access                   = true
  enable_cluster_creator_admin_permissions = true
  vpc_id                                   = module.vpc.vpc_id
  subnet_ids                               = module.vpc.private_subnets
  compute_config                           = { enabled = true, node_pools = ["general-purpose"] }
  tags                                     = local.tags
}

resource "aws_route53_zone" "demo" {
  count = var.create_route53_zone ? 1 : 0
  name  = var.domain_name
  tags  = local.tags
}

resource "aws_ecr_repository" "service" {
  for_each = toset(["control-plane", "analysis-worker", "web-console"])

  name                 = "releasepilot/${each.key}"
  image_tag_mutability = "IMMUTABLE"
  force_delete         = false

  image_scanning_configuration { scan_on_push = true }
  encryption_configuration { encryption_type = "AES256" }
  tags = local.tags
}
