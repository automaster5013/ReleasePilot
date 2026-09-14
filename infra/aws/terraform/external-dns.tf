data "aws_iam_policy_document" "external_dns_assume" {
  statement {
    effect  = "Allow"
    actions = ["sts:AssumeRole", "sts:TagSession"]
    principals {
      type        = "Service"
      identifiers = ["pods.eks.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "external_dns" {
  count              = var.create_route53_zone ? 1 : 0
  name               = "${var.cluster_name}-external-dns"
  assume_role_policy = data.aws_iam_policy_document.external_dns_assume.json
  tags               = local.tags
}

data "aws_iam_policy_document" "external_dns" {
  count = var.create_route53_zone ? 1 : 0

  statement {
    actions   = ["route53:ChangeResourceRecordSets"]
    resources = [aws_route53_zone.demo[0].arn]
  }
  statement {
    actions   = ["route53:ListHostedZones", "route53:ListResourceRecordSets", "route53:ListTagsForResource"]
    resources = ["*"]
  }
}

resource "aws_iam_role_policy" "external_dns" {
  count  = var.create_route53_zone ? 1 : 0
  name   = "route53-record-management"
  role   = aws_iam_role.external_dns[0].id
  policy = data.aws_iam_policy_document.external_dns[0].json
}

resource "aws_eks_pod_identity_association" "external_dns" {
  count           = var.create_route53_zone ? 1 : 0
  cluster_name    = module.eks.cluster_name
  namespace       = "external-dns"
  service_account = "external-dns"
  role_arn        = aws_iam_role.external_dns[0].arn
}
