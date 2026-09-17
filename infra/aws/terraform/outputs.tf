output "cluster_name" { value = module.eks.cluster_name }
output "configure_kubectl" { value = "aws eks update-kubeconfig --region ${var.aws_region} --name ${module.eks.cluster_name}" }
output "route53_name_servers" {
  value       = var.create_route53_zone ? aws_route53_zone.demo[0].name_servers : []
  description = "가비아에 등록할 Route 53 위임 NS"
}
output "external_dns_role_arn" { value = var.create_route53_zone ? aws_iam_role.external_dns[0].arn : null }
output "github_release_role_arn" { value = aws_iam_role.github_release.arn }
output "audit_archive_bucket" { value = aws_s3_bucket.audit_archive.id }
output "audit_archive_role_arn" { value = aws_iam_role.audit_archive.arn }
output "estimated_cost_warning" { value = "EKS control plane, Auto Mode nodes, NAT Gateway and NLB incur charges. Destroy demo resources when not in use." }
output "cost_profile" {
  value = {
    environment                 = var.environment_profile
    eks_control_plane_log_types = var.eks_control_plane_log_types
    cloudwatch_retention_days   = var.cloudwatch_log_retention_days
    monthly_budget_usd          = var.budget_alert_email == null ? null : var.monthly_budget_usd
  }
}
