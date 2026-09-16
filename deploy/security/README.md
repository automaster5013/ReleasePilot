# ReleasePilot artifact admission

ReleasePilot 운영 namespace는 GitHub artifact attestation이 유효한 Docker Hub 이미지로 제한한다. GitHub 공식 구성과 동일하게 policy-controller 0.10.5 및 trust-policies v0.7.0을 고정한다.

```powershell
helm upgrade policy-controller --install --atomic --create-namespace `
  --namespace artifact-attestations `
  oci://ghcr.io/sigstore/helm-charts/policy-controller `
  --version 0.10.5

helm upgrade trust-policies --install --atomic `
  --namespace artifact-attestations `
  oci://ghcr.io/github/artifact-attestations-helm-charts/trust-policies `
  --version v0.7.0 `
  --values deploy/security/github-attestation-policy-values.yaml
```

두 Helm release와 webhook Pod가 준비된 뒤에만 `releasepilot` namespace의 `policy.sigstore.dev/include=true` label을 적용한다. 저장소의 namespace manifest가 이 label을 유지한다. 장애 시 label을 제거하면 새 Pod admission 강제를 중단할 수 있으며, controller나 trust policy 삭제보다 먼저 수행한다.
