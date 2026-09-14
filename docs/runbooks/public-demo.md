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
5. GitHub Actions 변수 `AWS_RELEASE_ROLE_ARN`, `GITOPS_REPOSITORY`와 secret `GITOPS_REPOSITORY_TOKEN`을 설정한다.
6. 태그 빌드가 만든 digest PR을 병합한 뒤 `deploy/argocd/application.yaml`의 저장소 URL을 실제 GitOps 저장소로 바꾸어 적용한다.

## 검증

```powershell
Resolve-DnsName releasepilot.kr
curl.exe -fsS https://releasepilot.kr/health
curl.exe -fsS https://releasepilot.kr/actuator/health
kubectl -n releasepilot get pods,pvc,ingress
kubectl -n cert-manager get challenges,certificates -A
```

브라우저에서 `읽기 전용 데모`를 누른 뒤 판정 근거가 표시되는지 확인한다. 공개 세션의 역할은 `VIEWER`여야 하며 Promote, Pause, Abort 버튼은 비활성 상태여야 한다. 서버 통합 테스트도 VIEWER의 Abort 요청이 403임을 검증한다. 데모 계정은 쓰기 권한이 없고 세션이 한 시간 뒤 만료되므로 별도 데이터 reset 작업이 필요하지 않다.

## 실패 시

- DNS가 없으면 external-dns Pod Identity association과 controller 로그를 확인한다.
- 인증서 발급이 멈추면 cert-manager Challenge와 ingress-nginx의 80 포트 접근을 확인한다.
- 앱이 시작되지 않으면 `releasepilot-runtime` Secret, MySQL PVC와 Flyway 로그를 확인한다.
- Canary가 멈추면 Argo Rollouts controller, Rollout 이벤트와 Analysis Worker 연결을 확인한다.

## 종료 및 비용 차단

GitOps 동기화를 중지하고 `terraform destroy` 계획에서 대상이 ReleasePilot demo VPC/EKS/Route53/ECR인지 확인한 뒤 실행한다. ECR 이미지가 남아 있으면 `force_delete=false` 때문에 삭제가 중단되며, 보존 여부를 결정한 후 별도로 정리한다.
