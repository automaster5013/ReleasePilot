# Kubernetes 이미지 provenance admission

Docker Hub CD가 발급한 GitHub artifact attestation을 EKS admission 단계에서 다시 검증한다. Sigstore policy-controller 0.10.5와 GitHub trust-policies v0.7.0을 `artifact-attestations` namespace에 Helm release로 설치한다. 정책은 GitHub 소유자 `automaster5013`의 `ReleasePilot` 저장소와 `index.docker.io/automaster5013/releasepilot-**` 이미지로 한정한다.

운영 namespace의 MySQL은 ReleasePilot CI에서 빌드하지 않으므로 `index.docker.io/library/mysql**`만 명시적으로 예외 처리한다. 다른 이미지 패턴은 허용하지 않는다. `releasepilot` namespace의 `policy.sigstore.dev/include=true` label이 실제 enforcement를 켠다.

2026-09-16 운영 검증에서 현재 서명된 control-plane digest와 `mysql:8.4`의 server-side dry-run Pod admission은 성공했다. provenance 도입 이전 control-plane digest는 webhook이 `no valid bundles exist in registry`로 거부했다. dry-run이므로 테스트 Pod나 운영 mutation은 생성하지 않았다. 이후 기존 세 Rollout은 Healthy 2/2, Argo CD는 Synced/Healthy 상태를 유지했다.

이 정책은 새 Pod 생성·변경 admission을 검사한다. 이미 실행 중인 Pod를 소급 종료하지 않으며, registry 가용성이나 Sigstore 검증 서비스 장애가 발생하면 새 ReleasePilot Pod admission이 fail-closed 될 수 있다. 긴급 중단 절차는 먼저 namespace label을 제거하는 것이며 Helm release 삭제는 그 다음 단계다. 정책 범위나 MySQL 이미지를 변경할 때는 새 예외를 포괄 wildcard로 추가하지 않고 정확한 registry 경계를 검토한다.

webhook은 replica 2개, `failurePolicy: Fail`, PDB `minAvailable: 1`로 운영한다. 2026-09-16 한 webhook Pod를 강제 삭제한 직후 현재 서명 digest의 server-side dry-run admission이 성공했고, 대체 Pod가 준비되어 다시 2/2가 됐다. 현재 EKS Auto Mode 노드는 하나이므로 이는 webhook 프로세스/Pod 장애 내구성 검증이며 노드·가용 영역 장애 내구성을 뜻하지 않는다. 노드 수준 고가용성에는 두 노드 이상과 required topology 분산, 비용·용량 정책 검토가 추가로 필요하다.
