data "aws_caller_identity" "current" {}

resource "aws_s3_bucket" "audit_archive" {
  bucket              = "${var.cluster_name}-audit-${data.aws_caller_identity.current.account_id}"
  object_lock_enabled = true
  force_destroy       = false
  tags                = merge(local.tags, { DataClass = "Audit" })
}

resource "aws_s3_bucket_versioning" "audit_archive" {
  bucket = aws_s3_bucket.audit_archive.id
  versioning_configuration { status = "Enabled" }
}

resource "aws_s3_bucket_server_side_encryption_configuration" "audit_archive" {
  bucket = aws_s3_bucket.audit_archive.id
  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

resource "aws_s3_bucket_public_access_block" "audit_archive" {
  bucket                  = aws_s3_bucket.audit_archive.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_object_lock_configuration" "audit_archive" {
  bucket = aws_s3_bucket.audit_archive.id
  rule {
    default_retention {
      mode = "COMPLIANCE"
      days = var.audit_retention_days
    }
  }
  depends_on = [aws_s3_bucket_versioning.audit_archive]
}

data "aws_iam_policy_document" "audit_archive_assume" {
  statement {
    actions = ["sts:AssumeRole", "sts:TagSession"]
    principals {
      type        = "Service"
      identifiers = ["pods.eks.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "audit_archive" {
  name               = "${var.cluster_name}-audit-archive"
  assume_role_policy = data.aws_iam_policy_document.audit_archive_assume.json
  tags               = local.tags
}

data "aws_iam_policy_document" "audit_archive" {
  statement {
    sid       = "AppendAuditObjects"
    actions   = ["s3:PutObject"]
    resources = ["${aws_s3_bucket.audit_archive.arn}/audit-events/*"]
  }
  statement {
    sid       = "LocateAuditBucket"
    actions   = ["s3:GetBucketLocation"]
    resources = [aws_s3_bucket.audit_archive.arn]
  }
}

resource "aws_iam_role_policy" "audit_archive" {
  name   = "append-only-audit-archive"
  role   = aws_iam_role.audit_archive.id
  policy = data.aws_iam_policy_document.audit_archive.json
}

resource "aws_eks_pod_identity_association" "audit_archive" {
  cluster_name    = module.eks.cluster_name
  namespace       = "releasepilot"
  service_account = "releasepilot-control-plane"
  role_arn        = aws_iam_role.audit_archive.arn
}

resource "aws_s3_bucket_policy" "audit_archive_tls" {
  bucket = aws_s3_bucket.audit_archive.id
  policy = jsonencode({ Version = "2012-10-17", Statement = [{ Sid = "DenyInsecureTransport", Effect = "Deny", Principal = "*", Action = "s3:*", Resource = [aws_s3_bucket.audit_archive.arn, "${aws_s3_bucket.audit_archive.arn}/*"], Condition = { Bool = { "aws:SecureTransport" = "false" } } }] })
}
