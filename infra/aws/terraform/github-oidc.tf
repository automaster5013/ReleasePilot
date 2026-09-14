resource "aws_iam_openid_connect_provider" "github" {
  url             = "https://token.actions.githubusercontent.com"
  client_id_list  = ["sts.amazonaws.com"]
  thumbprint_list = ["6938fd4d98bab03faadb97b34396831e3780aea1"]
}

data "aws_iam_policy_document" "github_release_assume_role" {
  statement {
    actions = ["sts:AssumeRoleWithWebIdentity"]
    effect  = "Allow"

    principals {
      type        = "Federated"
      identifiers = [aws_iam_openid_connect_provider.github.arn]
    }

    condition {
      test     = "StringEquals"
      variable = "token.actions.githubusercontent.com:aud"
      values   = ["sts.amazonaws.com"]
    }

    condition {
      test     = "StringLike"
      variable = "token.actions.githubusercontent.com:sub"
      values = [
        "repo:automaster5013/ReleasePilot:ref:refs/tags/v*",
        "repo:automaster5013/releasepilot:ref:refs/tags/v*",
      ]
    }
  }
}

resource "aws_iam_role" "github_release" {
  name               = "releasepilot-github-release"
  assume_role_policy = data.aws_iam_policy_document.github_release_assume_role.json
  tags               = local.tags
}

data "aws_iam_policy_document" "github_release_ecr" {
  statement {
    actions   = ["ecr:GetAuthorizationToken"]
    resources = ["*"]
  }

  statement {
    actions = [
      "ecr:BatchCheckLayerAvailability",
      "ecr:CompleteLayerUpload",
      "ecr:GetDownloadUrlForLayer",
      "ecr:InitiateLayerUpload",
      "ecr:PutImage",
      "ecr:UploadLayerPart",
    ]
    resources = [for repository in aws_ecr_repository.service : repository.arn]
  }
}

resource "aws_iam_role_policy" "github_release_ecr" {
  name   = "releasepilot-ecr-publish"
  role   = aws_iam_role.github_release.id
  policy = data.aws_iam_policy_document.github_release_ecr.json
}
