# 다중 클러스터 운영

ReleasePilot은 Environment마다 하나의 `ClusterConnection`을 명시적으로 참조한다. 여러 클러스터를
등록하고 같은 서비스의 서로 다른 Environment를 각 클러스터에 배치할 수 있지만, 하나의 릴리스가
암묵적으로 여러 클러스터에 fan-out되지는 않는다. 이 경계는 일부 클러스터 장애나 잘못된 권한이 다른
클러스터 배포로 전파되는 것을 막는다.

## 연결 검증

Operator는 개별 연결 또는 등록된 모든 연결을 검증할 수 있다.

```http
POST /api/v1/connections/clusters/{clusterId}/validate
POST /api/v1/connections/clusters/validate
```

검증은 Kubernetes `/version`과 각 `allowedNamespaces`에 대한 Argo Rollouts `get`, `watch`, `patch`
SelfSubjectAccessReview를 수행한다. 모든 namespace에 필요한 권한이 있을 때만 연결을 `ACTIVE`로
표시한다. secret 부재, 인증 실패, 네트워크 오류, 권한 부족은 안정적인 `failureCode`와 함께
`INVALID`로 닫힌다. 응답과 AuditEvent에는 bearer token이나 secret 값이 포함되지 않는다.

릴리스 요청 시 Environment 검증뿐 아니라 대상 ClusterConnection이 `ACTIVE`인지 다시 확인한다.
따라서 다른 클러스터가 정상이어도 검증되지 않았거나 권한을 잃은 대상 클러스터로는 새 릴리스를
시작할 수 없다. 연결 검증 결과가 오래되었다면 운영 절차에서 재검증한 후 릴리스를 요청한다.

## 등록 예시

클러스터별 service account token은 저장소나 DB에 넣지 않고 서로 다른 환경 변수로 주입한다.

```json
{
  "name": "seoul-production",
  "apiServer": "https://example.eks.amazonaws.com",
  "allowedNamespaces": ["checkout", "payments"],
  "secretRef": "env:SEOUL_PRODUCTION_K8S_TOKEN"
}
```

두 클러스터에서 같은 이름의 namespace나 Rollout을 사용해도 실행 레코드는 `clusterId`, namespace,
Rollout UID와 resourceVersion을 함께 보관하므로 대상 식별이 섞이지 않는다.
