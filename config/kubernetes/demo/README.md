# Argo Rollouts demo

Argo Rollouts controller가 설치된 클러스터에 다음 명령으로 MVP 검증용 10→30→60→100 Canary를 배포한다.

최근 Argo Rollouts 배포 파일의 대형 CRD는 client-side apply의 annotation 제한을 넘을 수 있으므로 controller 설치에는 server-side apply를 사용한다.

```powershell
kubectl create namespace argo-rollouts
kubectl apply --server-side -n argo-rollouts -f https://github.com/argoproj/argo-rollouts/releases/latest/download/install.yaml
kubectl rollout status deployment/argo-rollouts -n argo-rollouts --timeout=180s
kubectl apply -k C:\ReleasePilot\config\kubernetes\demo
kubectl wait --for=condition=Available rollout/releasepilot-demo -n releasepilot-demo --timeout=180s
```

Environment 등록값은 namespace와 rolloutName, containerName에 각각 `releasepilot-demo`를 사용하고 stable/canary Service는 `demo-stable`, `demo-canary`를 사용한다.

## M3 실클러스터 검증 결과

2026-09-14에 kind Kubernetes v1.37.0과 Argo Rollouts v1.10.0으로 다음을 확인했다.

- 새 이미지가 10%, 30%, 60%, 100% 정지점을 순서대로 통과했다.
- 각 정지점의 Argo 원시 인덱스 1, 3, 5, 7을 ReleasePilot 논리 단계 0, 1, 2, 3으로 변환했다.
- 마지막 승격 후 phase가 `Healthy`, 원시 인덱스가 8이 되고 stable Service가 새 ReplicaSet으로 전환됐다.
- 다음 revision을 첫 정지점에서 abort한 뒤 stable Service가 기존 ReplicaSet을 계속 선택하고 stable replica 5개가 Ready임을 확인했다.
