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
variable "audit_retention_days" {
  type        = number
  default     = 30
  description = "S3 Object Lock compliance retention for immutable audit objects"
  validation {
    condition     = var.audit_retention_days >= 1
    error_message = "audit_retention_days must be at least one day."
  }
}
