variable "aws_region" {
  type    = string
  default = "ap-northeast-2"
}
variable "cluster_name" {
  type    = string
  default = "releasepilot-demo"
}
variable "domain_name" {
  type    = string
  default = "releasepilot.kr"
}
variable "create_route53_zone" {
  type    = bool
  default = true
}
variable "owner" {
  type    = string
  default = "releasepilot"
}

variable "environment_profile" {
  type        = string
  default     = "demo-low-cost"
  description = "Cost and observability profile recorded in resource tags"
  validation {
    condition     = contains(["demo-low-cost", "demo-ha", "production-like"], var.environment_profile)
    error_message = "environment_profile must be demo-low-cost, demo-ha, or production-like."
  }
}

variable "eks_control_plane_log_types" {
  type        = list(string)
  default     = ["api", "authenticator"]
  description = "EKS control-plane logs sent to CloudWatch. Audit is intentionally excluded from the low-cost demo default."
  validation {
    condition = alltrue([
      for log_type in var.eks_control_plane_log_types :
      contains(["api", "audit", "authenticator", "controllerManager", "scheduler"], log_type)
    ])
    error_message = "Unsupported EKS control-plane log type."
  }
}

variable "cloudwatch_log_retention_days" {
  type        = number
  default     = 7
  description = "Retention for EKS control-plane logs"
  validation {
    condition     = contains([1, 3, 5, 7, 14, 30, 60, 90, 120, 150, 180, 365, 400, 545, 731, 1096, 1827, 2192, 2557, 2922, 3288, 3653], var.cloudwatch_log_retention_days)
    error_message = "cloudwatch_log_retention_days must be a CloudWatch Logs supported value."
  }
}

variable "ecr_tagged_images_to_keep" {
  type        = number
  default     = 10
  description = "Number of recent tagged images retained per ECR repository"
  validation {
    condition     = var.ecr_tagged_images_to_keep >= 2
    error_message = "Keep at least two tagged images to preserve rollback capability."
  }
}

variable "budget_alert_email" {
  type        = string
  default     = null
  nullable    = true
  description = "Optional email for AWS Budget alerts. Leave null until the recipient is confirmed."
  validation {
    condition     = var.budget_alert_email == null || can(regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$", var.budget_alert_email))
    error_message = "budget_alert_email must be null or a valid email address."
  }
}

variable "monthly_budget_usd" {
  type        = number
  default     = 20
  description = "Monthly account cost budget in USD"
  validation {
    condition     = var.monthly_budget_usd > 0
    error_message = "monthly_budget_usd must be greater than zero."
  }
}
variable "audit_retention_days" {
  type        = number
  default     = 30
  description = "S3 Object Lock compliance retention for immutable audit objects"
  validation {
    condition     = var.audit_retention_days >= 1
    error_message = "audit_retention_days must be at least one day."
  }
}

variable "runtime_secret_name" {
  type        = string
  default     = "releasepilot/production/runtime"
  description = "AWS Secrets Manager name read by External Secrets Operator"
  validation {
    condition     = length(trimspace(var.runtime_secret_name)) > 0
    error_message = "runtime_secret_name must not be empty."
  }
}
