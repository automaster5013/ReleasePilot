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
7. `infra/aws/platform/bootstrap-gitops.ps1`을 실행한다. 스크립트가 상위 `releasepilot-platform` Application을 적용하고 상위 앱과 세 하위 앱이 main을 Synced/Healthy로 표시할 때까지 확인한다.

## 검증

```powershell
Resolve-DnsName releasepilot.kr
curl.exe -fsS https://releasepilot.kr/health
curl.exe -fsS https://releasepilot.kr/actuator/health
kubectl -n releasepilot get pods,pvc,ingress
kubectl get challenges,certificates -A
```

브라우저에서 `읽기 전용 데모`를 누른 뒤 판정 근거가 표시되는지 확인한다. 공개 세션의 역할은 `VIEWER`여야 하며 상태 변경 조작을 사용할 수 없어야 한다. 서버 통합 테스트도 VIEWER의 Abort 요청이 403임을 검증한다. 일반 세션의 비활성 만료 기본값은 30분이고, demo session은 생성 시 60분으로 설정된다. 이는 로그인 시점부터의 고정 만료 시간이 아니라 마지막 접근 기준의 비활성 만료다. 공개 세션은 데모 데이터를 변경할 수 없으므로 공개 조회 자체에는 별도 reset이 필요하지 않다.

릴리스 검증은 Git에 임시 변경을 만들지 않고 `v*` 태그를 push해 수행한다. workflow 성공 후 overlay의 세
digest가 바뀌고 Argo CD가 최종 Git revision을 동기화하는지 확인한다. ReleasePilot 자체의 세 Rollout은
20% → 60초 대기 → 50% → 수동 대기 단계다. 50%에서 새 Pod의 준비 상태, 재시작 수, 이미지 digest,
오류 로그와 공개 헬스를 확인한 뒤 Argo Rollouts의 수동 승격을 수행한다. 이후 세 Rollout이 Healthy,
ready/updated/available이 각각 2이며 Argo CD가 최종 revision에 Synced/Healthy인지 확인한다.
이는 ReleasePilot이 관리하는 대상 앱의 정책별 Canary 단계와 별개다. 실패 시나리오는
분석/상태 머신 통합 테스트로 반복하며, 공개 VIEWER가 실제 운영 Rollout을 변경하는 시연은 하지 않는다.

## 실패 시

- DNS가 없으면 external-dns Pod Identity association과 controller 로그를 확인한다.
- 인증서 발급이 멈추면 cert-manager Challenge와 ingress-nginx의 80 포트 접근을 확인한다.
- 앱이 시작되지 않으면 `releasepilot-runtime` Secret, MySQL PVC와 Flyway 로그를 확인한다.
- Canary가 멈추면 Argo Rollouts controller, Rollout 이벤트와 Analysis Worker 연결을 확인한다.

## 종료 및 비용 차단

GitOps 동기화를 중지하고 `infra/aws/terraform`에서 destroy 계획의 대상이 ReleasePilot demo 자원인지 확인한 뒤 승인된 범위만 제거한다. ECR 이미지가 남아 있으면 `force_delete=false` 때문에 삭제가 중단되며, 보존 여부를 결정한 후 별도로 정리한다. 감사 S3 버킷은 `force_destroy=false`이며 COMPLIANCE Object Lock의 보존 기간 중인 객체는 삭제할 수 없다. 따라서 전체 자원이 즉시 제거된다고 가정하지 말고, 보존 자원과 잔여 과금을 따로 확인한다. 감사 보관 정책은 [감사 무결성·외부 보관 문서](../operations/audit-integrity-and-archive.md)를 따른다.

## 알려진 제한

- 데모 MySQL은 단일 StatefulSet/PVC이므로 다중 AZ 복구와 관리형 백업을 보장하지 않는다.
- Grafana, Prometheus 서버와 Kubernetes API는 공개하지 않는다. Control Plane의
  `/actuator/prometheus`는 내부 서비스에서 인증 없이 접근 가능하지만 AWS Ingress는
  `/actuator/health`, `/actuator/health/liveness`, `/actuator/health/readiness`만 Exact 경로로 전달한다.
  공개 `/actuator/prometheus`와 `/actuator/info`는 Web Console의 404로 차단된다. 운영 전환 시에는
  내부 모니터링의 네트워크 범위와 인증도 별도로 검토한다.
- 공개 방문자에게는 VIEWER demo session을 제공한다. 조직 OIDC/SSO와 역할별 운영 UI는 구현돼 있지만,
  기본값은 `OIDC_ENABLED=false`이며 공급자 등록·client secret·내부 계정/프로젝트 권한 연결이 필요하다.
  활성화 절차는 [인증 문서](../security/authentication-and-demo-access.md)를 따른다. 저장소 정의만으로
  실제 공급자 연결 완료를 판단하지 않는다.
- GitHub Checks 전달은 구현돼 있지만 기본값은 `GITHUB_CHECKS_ENABLED=false`다. 활성화에는 repository의
  Checks 쓰기 권한과 런타임 token 주입·갱신이 필요하다. [연동 문서](../integrations/github-checks.md)를 따른다.
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
- 인증 endpoint의 MySQL 공유 rate limit은 구현됐고 기본 활성화된다. AWS overlay는 S3 감사 보관을
  활성화한다. 보관 전달 실패·재시도와 해시 체인 무결성은 운영 중 점검해야 한다.
- 운영 전환의 후속 작업은 RDS와 백업/복구 훈련, Secrets Manager/Vault 연결 및 비밀 자동 교체,
  WAF와 관측성 접근 통제, 실제 조직 SSO·GitHub Checks 연결 검증이다. 현재 secret resolver는 `env:`만
  지원하며 `vault:` reference를 등록하는 것만으로 Vault가 연결되지는 않는다.
