# ReleasePilot artifact admission

ReleasePilot 운영 namespace는 GitHub artifact attestation이 유효한 Docker Hub 이미지로 제한한다. GitHub 공식 구성과 동일하게 policy-controller 0.10.5 및 trust-policies v0.7.0을 고정한다.

Argo CD Application은 OCI Helm chart 버전과 이 디렉터리의 values 파일을 함께 선언한다. 클러스터를 복구하거나 새로 부트스트랩할 때 policy-controller를 먼저 적용하고 Healthy/Synced 상태를 확인한 뒤 trust policy를 적용한다.

```powershell
kubectl apply -f deploy/argocd/artifact-policy-controller.yaml
kubectl wait --for=jsonpath='{.status.health.status}'=Healthy `
  application/artifact-policy-controller -n argocd --timeout=5m
kubectl apply -f deploy/argocd/artifact-trust-policies.yaml
kubectl wait --for=jsonpath='{.status.health.status}'=Healthy `
  application/artifact-trust-policies -n argocd --timeout=5m
```

설치 후 리소스 수명주기는 Argo CD가 담당한다. 수동 `helm upgrade`를 함께 실행하지 않는다. `deploy/argocd/kustomization.yaml`은 이미 준비된 클러스터에서 모든 Application 선언을 한 번에 등록하는 부트스트랩 진입점이다.

두 Helm release와 webhook Pod가 준비된 뒤에만 `releasepilot` namespace의 `policy.sigstore.dev/include=true` label을 적용한다. 저장소의 namespace manifest가 이 label을 유지한다. 장애 시 label을 제거하면 새 Pod admission 강제를 중단할 수 있으며, controller나 trust policy 삭제보다 먼저 수행한다.
