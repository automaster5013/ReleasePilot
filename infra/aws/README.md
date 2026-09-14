# AWS 공개 데모

EKS Auto Mode와 NGINX Ingress를 사용한다. EKS는 compute/network/load-balancer 운영 부담을 줄이고,
NGINX는 Argo Rollouts가 Ingress annotation으로 실제 트래픽 비율을 제어할 수 있게 한다.

## 적용 순서

1. `terraform/terraform.tfvars.example`을 `terraform.tfvars`로 복사하고 `terraform init && terraform apply`한다.
2. 출력된 `route53_name_servers`를 가비아 도메인의 네임서버로 등록한다.
3. 출력된 `configure_kubectl`을 실행한다.
4. `platform/bootstrap.ps1`로 Argo CD, Argo Rollouts, ingress-nginx, cert-manager를 설치한다.
5. 별도 GitOps 저장소에서 `argocd/releasepilot-demo.yaml`의 `repoURL`을 바꾸고 적용한다.

Terraform은 기본적으로 NAT Gateway 한 개를 만든다. 데모를 계속 운영하지 않을 때는 `terraform destroy`로
과금 자원을 제거한다. 실제 apply 전 `terraform plan`의 리소스와 예상 비용을 검토한다.
