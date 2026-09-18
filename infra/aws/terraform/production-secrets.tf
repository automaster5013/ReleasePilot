resource "aws_secretsmanager_secret" "releasepilot_runtime" {
  name                    = var.runtime_secret_name
  description             = "ReleasePilot production runtime configuration; values are populated out of band"
  recovery_window_in_days = 30
  kms_key_id              = aws_kms_key.runtime_secrets.arn
  tags                    = merge(local.tags, { Environment = "production" })
}

resource "aws_kms_key" "runtime_secrets" {
  description             = "ReleasePilot production runtime secrets"
  deletion_window_in_days = 30
  enable_key_rotation     = true
  tags                    = merge(local.tags, { Environment = "production" })
}

resource "aws_kms_alias" "runtime_secrets" {
  name          = "alias/${var.cluster_name}-runtime-secrets"
  target_key_id = aws_kms_key.runtime_secrets.key_id
}

data "aws_iam_policy_document" "external_secrets_assume" {
  statement {
    effect  = "Allow"
    actions = ["sts:AssumeRole", "sts:TagSession"]
    principals {
      type        = "Service"
      identifiers = ["pods.eks.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "external_secrets" {
  name               = "${var.cluster_name}-external-secrets"
  assume_role_policy = data.aws_iam_policy_document.external_secrets_assume.json
  tags               = local.tags
}

data "aws_iam_policy_document" "external_secrets" {
  statement {
    actions   = ["secretsmanager:GetSecretValue", "secretsmanager:DescribeSecret"]
    resources = [aws_secretsmanager_secret.releasepilot_runtime.arn]
  }
  statement {
    actions   = ["kms:Decrypt"]
    resources = [aws_kms_key.runtime_secrets.arn]
  }
}

resource "aws_iam_role_policy" "external_secrets" {
  name   = "read-releasepilot-runtime-secret"
  role   = aws_iam_role.external_secrets.id
  policy = data.aws_iam_policy_document.external_secrets.json
}

resource "aws_eks_pod_identity_association" "external_secrets" {
  cluster_name    = module.eks.cluster_name
  namespace       = "external-secrets"
  service_account = "external-secrets"
  role_arn        = aws_iam_role.external_secrets.arn
}
