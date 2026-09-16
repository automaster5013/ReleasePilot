# AWS 공개 데모

EKS Auto Mode와 NGINX Ingress를 사용한다. EKS는 compute/network/load-balancer 운영 부담을 줄이고,
NGINX는 HTTPS와 경로 라우팅을 담당한다. 현재 세 애플리케이션 Rollout에는 Ingress trafficRouting이
설정돼 있지 않으며 replica-weight Canary를 사용하므로 요청 단위의 정확한 비율은 보장하지 않는다.

## 적용 순서

1. `terraform/terraform.tfvars.example`을 `terraform/terraform.tfvars`로 복사하고 `terraform` 디렉터리에서 init과 plan을 실행한다. 계획 검토 후 승인된 범위로 apply한다.
2. 출력된 `route53_name_servers`를 가비아 도메인의 네임서버로 등록한다.
3. 출력된 `configure_kubectl`을 실행한다.
4. `platform/bootstrap.ps1`로 Argo CD, Argo Rollouts, ingress-nginx, cert-manager를 설치한다.
5. 저장소 루트에서 `kubectl apply -k deploy/argocd`를 실행해 `releasepilot-platform`을 적용한다. 기본 GitOps source는 이 저장소의 main과
   `deploy/overlays/aws-demo`다. 다른 저장소를 사용할 때만 repoURL과 경로를 변경한다.

Terraform은 기본적으로 NAT Gateway 한 개를 만든다. 데모를 계속 운영하지 않을 때는 `terraform destroy`로
과금 자원의 제거 계획을 검토한다. 감사 S3 객체는 COMPLIANCE Object Lock 보존 기간 중 삭제할 수 없고,
ECR과 감사 버킷은 강제 삭제가 비활성화돼 있으므로 잔여 자원과 과금을 별도로 확인한다.
검증·수동 Canary 승격·복구·종료 절차는 [공개 데모 runbook](../../docs/runbooks/public-demo.md)을 따른다.
