# ReleasePilot artifact admission

ReleasePilot 운영 namespace는 GitHub artifact attestation이 유효한 Docker Hub 이미지로 제한한다. GitHub 공식 구성과 동일하게 policy-controller 0.10.5 및 trust-policies v0.7.0을 고정한다.

Argo CD Application은 OCI Helm chart 버전과 이 디렉터리의 values 파일을 함께 선언한다. 클러스터를 복구하거나 새로 부트스트랩할 때 policy-controller를 먼저 적용하고 Healthy/Synced 상태를 확인한 뒤 trust policy를 적용한다.

```powershell
kubectl apply -f deploy/argocd/apps/artifact-policy-controller.yaml
kubectl wait --for=jsonpath='{.status.health.status}'=Healthy `
  application/artifact-policy-controller -n argocd --timeout=5m
kubectl apply -f deploy/argocd/apps/artifact-trust-policies.yaml
kubectl wait --for=jsonpath='{.status.health.status}'=Healthy `
  application/artifact-trust-policies -n argocd --timeout=5m
```

설치 후 리소스 수명주기는 Argo CD가 담당한다. 수동 `helm upgrade`를 함께 실행하지 않는다. 일반 부트스트랩에서는 `kubectl apply -k deploy/argocd`로 상위 `releasepilot-platform` Application 하나만 등록한다. 상위 앱이 `deploy/argocd/apps`의 하위 Application을 생성·복구하며 policy-controller가 trust policy보다 먼저 동기화된다.

기존 수동 Helm 설치에서 전환할 때는 두 Argo CD Application이 모두 `Synced/Healthy`이고 admission 검증이 성공한 뒤 Helm release Secret만 백업·제거한다. `helm uninstall`은 운영 리소스를 삭제하므로 전환 절차에 사용하지 않는다. 전환 후 `helm list -n artifact-attestations`는 비어 있어야 한다.

policy-controller는 chart가 만든 최소 webhook 정의에 인증서, rules, selector를 런타임에 채운다. Argo CD는 해당 두 webhook의 `webhooks` 배열을 drift 비교에서 제외하고 chart values, Deployment, RBAC, CRD 및 나머지 리소스를 계속 조정한다. `failurePolicy: Fail`과 replica/PDB/affinity는 저장소 테스트와 운영 검증에서 별도로 확인한다.

두 Helm release와 webhook Pod가 준비된 뒤에만 `releasepilot` namespace의 `policy.sigstore.dev/include=true` label을 적용한다. 저장소의 namespace manifest가 이 label을 유지한다. 장애 시 label을 제거하면 새 Pod admission 강제를 중단할 수 있으며, controller나 trust policy 삭제보다 먼저 수행한다.
