# ReleasePilot API 규칙

## 기본 규칙

- 기준 계약은 `api/openapi/releasepilot-v1.yaml`이다.
- 공개 경로는 `/api/v1`을 사용한다.
- 브라우저 인증은 `RELEASEPILOT_SESSION` HttpOnly cookie를 사용한다.
- POST, PUT, PATCH, DELETE 요청은 세션 응답의 CSRF token을 `X-CSRF-TOKEN` header로 전달한다.
- 명령 요청과 응답은 JSON, 오류는 `application/problem+json`을 사용한다.
- 모든 시간은 RFC 3339 UTC 문자열로 전달한다.
- ID는 UUID 형식이며 데이터베이스 순차 ID를 노출하지 않는다.
- 목록은 cursor 방식으로 페이지를 나눈다.
- 모든 HTTP 응답은 `X-Correlation-ID`를 포함한다. 유효한 UUID 요청 header는 그대로 사용하고,
  없거나 잘못된 값은 서버가 새 UUID로 교체한다.

## 명령과 멱등성

상태를 변경하는 핵심 요청은 `Idempotency-Key` header가 필수다.

- 릴리스 생성
- 승인 및 거부
- pause, resume, abort
- 정책 활성화

같은 사용자, operation, key 조합의 요청은 24시간 동안 같은 결과를 반환한다. 같은 key에 다른 요청 body가 전달되면 `409 Conflict`를 반환한다.

외부 시스템 작업이 필요한 운영 명령은 `202 Accepted`와 OperationReceipt를 반환한다. 접수 응답은 Argo Rollouts 명령 완료를 뜻하지 않는다.

## 오류 의미

| HTTP 상태 | 의미 |
|---:|---|
| 400 | JSON 형식, 타입 또는 필수 필드 오류 |
| 401 | 인증 정보 없음 또는 유효하지 않음 |
| 403 | 역할 또는 대상 리소스 권한 부족 |
| 404 | 요청한 리소스가 존재하지 않음 |
| 409 | 상태 전이, 활성 릴리스 또는 idempotency 충돌 |
| 422 | 정책이나 릴리스의 의미 검증 실패 |
| 503 | Kubernetes, Prometheus 등 필수 의존성 일시 장애 |

오류 응답은 RFC 9457 Problem Details를 따르고, 프로그램 처리를 위한 안정적인 `code`와 관찰용 `traceId`를 추가한다.

## 권한 기준

| 작업 | Viewer | Developer | Approver | Operator |
|---|:---:|:---:|:---:|:---:|
| demo 릴리스 조회 | O | O | O | O |
| 릴리스 생성 | X | O | O | O |
| 승인·거부 | X | X | O | O |
| pause·resume·abort | X | X | X | O |
| 정책 생성·활성화 | X | X | X | O |
| 감사 이벤트 조회 | 정제된 demo만 | 제한 | O | O |

production에서는 요청자가 자신의 릴리스를 승인할 수 없다. 역할 보유 여부와 별개로 분리 의무를 검사한다.

## 이벤트 전달

Web Console은 릴리스 상세 REST 조회 후 SSE `/releases/{releaseId}/events`를 구독한다.

- `release-state` 이벤트에는 릴리스 상태, Rollout 단계와 최근 분석 상태의 제한된 snapshot을 전달한다.
- 클라이언트는 분석 근거처럼 큰 데이터가 필요할 때 REST를 다시 조회한다.
- 연결이 끊기면 EventSource의 기본 재연결로 현재 snapshot을 다시 받는다.
- SSE는 감사 저장소의 무제한 원본 payload를 직접 노출하지 않는다.

## 아직 열려 있는 API 범위

- 사용자·Project membership 관리 API는 bootstrap 운영 방식 확정 후 추가한다.
- ClusterConnection과 PrometheusConnection 관리 API는 secret 저장 방식 확정 후 추가한다.
- Analysis Worker 전용 내부 API는 작업 전달 방식 ADR 이후 별도 내부 계약으로 작성한다.
