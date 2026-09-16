# Kubernetes 이미지 provenance admission

Docker Hub CD가 발급한 GitHub artifact attestation을 EKS admission 단계에서 다시 검증한다. Sigstore policy-controller 0.10.5와 GitHub trust-policies v0.7.0을 `artifact-attestations` namespace에 Helm release로 설치한다. 정책은 GitHub 소유자 `automaster5013`의 `ReleasePilot` 저장소와 `index.docker.io/automaster5013/releasepilot-**` 이미지로 한정한다.

운영 namespace의 MySQL은 ReleasePilot CI에서 빌드하지 않으므로 `index.docker.io/library/mysql**`만 명시적으로 예외 처리한다. 다른 이미지 패턴은 허용하지 않는다. `releasepilot` namespace의 `policy.sigstore.dev/include=true` label이 실제 enforcement를 켠다.

2026-09-16 운영 검증에서 현재 서명된 control-plane digest와 `mysql:8.4`의 server-side dry-run Pod admission은 성공했다. provenance 도입 이전 control-plane digest는 webhook이 `no valid bundles exist in registry`로 거부했다. dry-run이므로 테스트 Pod나 운영 mutation은 생성하지 않았다. 이후 기존 세 Rollout은 Healthy 2/2, Argo CD는 Synced/Healthy 상태를 유지했다.

이 정책은 새 Pod 생성·변경 admission을 검사한다. 이미 실행 중인 Pod를 소급 종료하지 않으며, registry 가용성이나 Sigstore 검증 서비스 장애가 발생하면 새 ReleasePilot Pod admission이 fail-closed 될 수 있다. 긴급 중단 절차는 먼저 namespace label을 제거하는 것이며 Helm release 삭제는 그 다음 단계다. 정책 범위나 MySQL 이미지를 변경할 때는 새 예외를 포괄 wildcard로 추가하지 않고 정확한 registry 경계를 검토한다.

webhook은 replica 2개, `failurePolicy: Fail`, PDB `minAvailable: 1`로 운영한다. required pod anti-affinity가 hostname과 zone을 모두 사용하므로 두 replica는 서로 다른 노드와 가용 영역에 배치된다. 2026-09-16 EKS Auto Mode가 `ap-northeast-2a`와 `ap-northeast-2c`에 노드를 준비한 뒤, 한 준비 상태 webhook 노드를 cordon하고 해당 Pod를 제거했다. 남은 zone의 replica가 현재 서명 digest admission을 계속 성공시켰고 대체 replica가 반대 zone에서 준비된 뒤 cordon을 해제했다. 최종 replica는 다시 2/2이며 이전 미서명 digest는 계속 거부됐다.

기존 두 replica가 이미 모든 사용 가능한 zone을 점유한 상태에서 처음 zone required affinity를 추가하면 Deployment의 surge Pod가 스케줄되지 않을 수 있다. 최초 전환에서는 운영 Rollout이 모두 Healthy임을 확인한 뒤 webhook Deployment를 잠시 0으로 축소하고 Helm atomic upgrade로 새 replica 2개를 생성했다. 이 구간에는 기존 서비스는 계속 실행되지만 `failurePolicy: Fail` 때문에 새 Pod admission이 일시 차단된다. 이후 동일 affinity를 유지하는 일반 재시작과 Pod 교체에는 이 전환 절차가 필요하지 않다.
