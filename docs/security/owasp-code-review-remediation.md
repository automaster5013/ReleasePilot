# OWASP 코드 점검 보완 사항

2026-09-19 코드 수준 점검에서 확인한 접근 제어, SSRF·자격증명 전달, 내부 Worker 인증,
PromQL 삽입 위험을 다음과 같이 보완했다.

- 릴리스 분석 결과 조회는 릴리스의 서비스와 프로젝트를 역참조해 `ProjectAccess.canView`를
  통과한 사용자에게만 결과를 반환한다. 권한이 없으면 리소스 존재 여부도 노출하지 않고 404를 반환한다.
- Kubernetes 및 Prometheus 연결 검증은 `CONNECTION_VALIDATION_ALLOWED_HOSTS`에 쉼표로
  나열된 정확한 호스트만 허용한다. 사용자 정보가 포함된 URL, 허용되지 않은 스킴과 호스트는
  자격증명을 전송하기 전에 `TARGET_NOT_ALLOWED`로 거부한다.
- Control Plane과 Analysis Worker는 `ANALYSIS_WORKER_SHARED_TOKEN`을 공유하고
  `X-ReleasePilot-Worker-Token` 헤더를 상수 시간 비교로 검증한다. 값이 없거나 다르면 fail-closed 한다.
- Analysis Worker는 `PROMETHEUS_ALLOWED_HOSTS`의 정확한 호스트에만 질의한다. 운영에서는
  `CONNECTION_VALIDATION_ALLOWED_HOSTS`와 같은 승인 목록을 두 변수에 설정한다.
- 로컬 compose의 Analysis Worker 포트는 `127.0.0.1`에만 바인딩한다.
- workload label 값은 Prometheus/Kubernetes label-safe 문자와 최대 63자로 제한한다.

운영 AWS Secrets Manager의 `releasepilot/production/runtime`에는 최소한 다음 값을 저장해야 한다.

```text
ANALYSIS_WORKER_SHARED_TOKEN=<충분히 긴 무작위 값>
CONNECTION_VALIDATION_ALLOWED_HOSTS=<Kubernetes API 및 Prometheus 정확한 호스트 목록>
PROMETHEUS_ALLOWED_HOSTS=<Prometheus 정확한 호스트 목록>
```

호스트 목록은 URL이나 경로가 아닌 DNS 호스트 이름만 사용하며 와일드카드는 지원하지 않는다.
연결 목적지가 변경되면 목록 변경, 재배포, 연결 재검증 순으로 진행한다.
