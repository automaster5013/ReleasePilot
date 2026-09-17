# AWS 비용 최적화 운영 절차

## 목표

ReleasePilot의 EKS, Argo Rollouts, admission policy, ingress 및 감사 아카이브 기능을 유지하면서
상시 데모 환경의 불필요한 CloudWatch와 ECR 비용을 제한한다. NAT Gateway, EKS control plane과 NLB는
클러스터가 존재하는 동안 고정비가 발생하므로 장기 미사용 환경은 노드 축소가 아니라 전체 제거를 검토한다.

## 비용 프로필

기본 `demo-low-cost` 프로필은 다음 값을 사용한다.

```hcl
environment_profile           = "demo-low-cost"
eks_control_plane_log_types   = ["api", "authenticator"]
cloudwatch_log_retention_days = 7
ecr_tagged_images_to_keep     = 10
monthly_budget_usd            = 20
```

보안 조사나 감사 증적 수집이 필요한 기간에만 `audit`을 추가한다.

```hcl
environment_profile         = "production-like"
eks_control_plane_log_types = ["api", "audit", "authenticator"]
```

감사 작업이 끝나면 반드시 `demo-low-cost` 값으로 되돌린다. 애플리케이션이 S3에 저장하는 ReleasePilot
감사 아카이브는 이 EKS control-plane 로그 설정과 별개이며 계속 유지된다.

## 변경 절차

1. `terraform fmt -recursive`와 `terraform validate`를 실행한다.
2. `terraform plan`에서 교체 또는 삭제가 없는지 확인한다.
3. 의도하지 않은 IAM, OIDC 또는 외부 변경 drift가 함께 나타나면 전체 apply를 중단한다.
4. 승인된 리소스만 적용한 후 EKS 로그 유형과 CloudWatch 보존 기간을 다시 조회한다.
5. 애플리케이션 Rollout, ingress, admission 및 audit archive 상태를 smoke test한다.

## ECR 보존 정책

각 ReleasePilot 저장소에서 다음 정책을 적용한다.

- untagged 이미지는 push 후 7일이 지나면 만료한다.
- tagged 이미지는 최근 10개를 유지한다.
- repository 자체는 삭제하지 않으며 `force_delete = false`를 유지한다.

이미지 정리는 비동기로 진행된다. 최근 10개를 넘어선 tagged 이미지가 실제 rollback에 필요하다면
`ecr_tagged_images_to_keep` 값을 먼저 늘린 후 plan을 검토한다.

## 예산 알림

`budget_alert_email`은 의도하지 않은 수신자 생성을 피하기 위해 기본값이 `null`이다. 수신자를 확정한 후
tfvars에 이메일을 설정하면 다음 알림이 생성된다.

- 월 예상 비용 50%
- 월 실제 비용 80%
- 월 실제 비용 100%

AWS가 보낸 구독 확인 메일을 수신자가 승인해야 한다. 예산은 알림만 전송하며 리소스를 자동 중지하지 않는다.

## 정기 점검

- Billing의 서비스별 비용과 credit 적용 전 비용을 매주 확인한다.
- CloudWatch `DataProcessing-Bytes`가 다시 증가하면 EKS의 enabled log types를 먼저 확인한다.
- ECR image count와 NAT processed bytes를 배포 빈도와 함께 확인한다.
- 데모를 장기간 사용하지 않으면 상태 보존 대상을 확인한 후 `terraform destroy` 계획을 별도 승인한다.

## 데모 컴퓨트 프로필

현재 계정의 Free Tier 자격 정책은 EKS Auto Mode가 새 `t3.medium` 노드를 기동하는 것을 허용하지 않는다.
따라서 `demo-low-cost`는 내장 `general-purpose` 풀의 기존 노드 한 대를 사용하고 admission webhook을 한
replica로 실행한다. 검증과 fail-closed 동작은 유지되지만 노드 및 webhook 고가용성은 제공하지 않는다.
단일 replica가 노드 통합을 막지 않도록 PDB도 비활성화한다. production-like 검증 전에는 webhook replica를
2로 복구하고 PDB `minAvailable: 1` 및 required cross-zone anti-affinity를 함께 복구한다.

## 보호 대상 경계

이 절차의 변경 대상은 `Project=ReleasePilot`, `releasepilot-*`, `releasepilot/*`로 명확히 식별되는 리소스에
한정한다. `VisionFlow-Drone` 인스턴스와 연결된 Elastic IP, EBS 볼륨, 스냅샷 및 기타 VisionFlow 리소스는
장기 보존 대상이며 ReleasePilot 비용 최적화나 정리 대상에 포함하지 않는다.
