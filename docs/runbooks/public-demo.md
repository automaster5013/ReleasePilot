# 공개 데모 배포 Runbook

## 사전 조건

- AWS 결제가 활성화된 계정과 서울 리전에 리소스를 만들 권한
- `releasepilot.kr`의 가비아 네임서버 변경 권한
- 이미지 및 GitOps 저장소가 있는 GitHub 조직
- 로컬의 AWS CLI, Terraform, kubectl

## 배포

1. `infra/aws/terraform/terraform.tfvars.example`을 복사해 값을 확인한다.
2. `infra/aws/terraform`에서 `terraform init`, `terraform plan`, `terraform apply`를 실행한다.
3. 출력된 `route53_name_servers` 네 개를 가비아 도메인의 네임서버로 등록한다.
4. 출력된 명령으로 kubeconfig를 설정하고 `infra/aws/platform/bootstrap.ps1`을 실행한다.
5. GitHub Actions 변수 `AWS_RELEASE_ROLE_ARN`을 OIDC 배포 역할 ARN으로 설정한다.
6. `v*` 태그 workflow가 세 이미지를 ECR에 push하고 main의 AWS overlay를 digest로 갱신하는지 확인한다.
7. `deploy/argocd/releasepilot-demo.yaml`을 적용하고 Argo CD가 main을 Synced/Healthy로 표시하는지 확인한다.

## 검증

```powershell
Resolve-DnsName releasepilot.kr
curl.exe -fsS https://releasepilot.kr/health
curl.exe -fsS https://releasepilot.kr/actuator/health
kubectl -n releasepilot get pods,pvc,ingress
kubectl get challenges,certificates -A
```

브라우저에서 `읽기 전용 데모`를 누른 뒤 판정 근거가 표시되는지 확인한다. 공개 세션의 역할은 `VIEWER`여야 하며 Promote, Pause, Abort 버튼은 비활성 상태여야 한다. 서버 통합 테스트도 VIEWER의 Abort 요청이 403임을 검증한다. 데모 계정은 쓰기 권한이 없고 세션이 한 시간 뒤 만료되므로 별도 데이터 reset 작업이 필요하지 않다.

릴리스 검증은 Git에 임시 변경을 만들지 않고 `v*` 태그를 push해 수행한다. workflow 성공 후 overlay의 세
digest가 바뀌고 Argo CD가 새 revision을 동기화하며 Rollout이 Healthy가 되는지 확인한다. 실패 시나리오는
분석/상태 머신 통합 테스트로 반복하며, 공개 VIEWER가 실제 운영 Rollout을 변경하는 시연은 하지 않는다.

## 실패 시

- DNS가 없으면 external-dns Pod Identity association과 controller 로그를 확인한다.
- 인증서 발급이 멈추면 cert-manager Challenge와 ingress-nginx의 80 포트 접근을 확인한다.
- 앱이 시작되지 않으면 `releasepilot-runtime` Secret, MySQL PVC와 Flyway 로그를 확인한다.
- Canary가 멈추면 Argo Rollouts controller, Rollout 이벤트와 Analysis Worker 연결을 확인한다.

## 종료 및 비용 차단

GitOps 동기화를 중지하고 `terraform destroy` 계획에서 대상이 ReleasePilot demo VPC/EKS/Route53/ECR인지 확인한 뒤 실행한다. ECR 이미지가 남아 있으면 `force_delete=false` 때문에 삭제가 중단되며, 보존 여부를 결정한 후 별도로 정리한다.

## 알려진 제한

- 데모 MySQL은 단일 StatefulSet/PVC이므로 다중 AZ 복구와 관리형 백업을 보장하지 않는다.
- Grafana, Prometheus와 Kubernetes API는 공개하지 않는다. 공개 콘솔에는 정제된 판정 증거만 표시한다.
- 공개 인증은 VIEWER demo session만 제공한다. 조직 SSO/OIDC와 운영자 UI 로그인은 MVP 이후 범위다.
- NGINX Canary traffic routing이 아닌 Argo Rollouts replica-weight 단계이므로 요청 단위의 정확한 비율은
  보장하지 않는다.
- AWS 리소스는 데모용 단일 계정·서울 리전에 한정된다.

## 비용 및 보안 점검

- 지속 비용의 주 항목은 EKS control plane, Auto Mode compute, NAT Gateway, NLB, EBS와 Route 53이다.
  사용하지 않을 때는 위 종료 절차로 전체 리소스를 제거한다.
- GitHub Actions는 장기 AWS access key 대신 repository/ref가 제한된 OIDC role을 사용하고 ECR 3개에만
  push한다.
- 이미지는 tag가 아닌 digest로 배포하며 Terraform state, kubeconfig, plan과 runtime secret은 Git에서
  제외한다.
- 외부 트래픽은 HTTPS로 강제하고 HSTS, CSP, frame 차단, MIME sniff 방지, Referrer/Permissions Policy를
  응답한다.
- 운영 전환에는 RDS, Secrets Manager, WAF/rate limit, 중앙 감사 보관, 백업/복구 훈련이 필요하다.
