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
