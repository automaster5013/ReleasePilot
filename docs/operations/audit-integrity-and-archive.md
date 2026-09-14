# 감사 이벤트 무결성과 외부 보관

ReleasePilot은 새 감사 이벤트마다 단조 증가하는 `chainSequence`, 직전 이벤트의 `previousHash`, 현재 이벤트의 `eventHash`를 저장한다. 해시는 이벤트 식별자, aggregate, actor, 발생 시각, correlation ID와 원본 JSON payload를 순서가 고정된 문자열로 직렬화한 뒤 SHA-256으로 계산한다.

`audit_chain_head` 행을 비관적 잠금으로 획득하므로 동시에 생성되는 이벤트도 하나의 전역 체인에 정확히 한 번 연결된다. 감사 이벤트와 `audit_archive_deliveries` 레코드는 업무 변경과 동일한 DB 트랜잭션에 기록된다. 외부 저장소 장애는 업무 트랜잭션을 되돌리지 않고 전달 상태를 `PENDING`으로 유지하며 최대 5분까지 지수 백오프로 재시도한다.

## 검증

OPERATOR는 `GET /api/v1/audit-events/verify`를 호출해 DB에 저장된 전체 해시 체인을 다시 계산할 수 있다. `valid=false`이면 `failedEventId`가 최초 불일치 이벤트를 가리킨다. V20 이전의 legacy 이벤트는 해시 필드가 없으므로 검증 대상에서 제외되고, V20 이후 체인은 genesis hash부터 시작한다.

## 외부 보관 설정

외부 WORM 저장소나 보관 게이트웨이가 HTTPS PUT과 멱등 키를 지원할 때 다음 환경 변수를 설정한다.

- `AUDIT_ARCHIVE_ENABLED=true`
- `AUDIT_ARCHIVE_ENDPOINT=https://archive.example/releasepilot/`
- `AUDIT_ARCHIVE_TOKEN`은 Kubernetes Secret 등 런타임 secret으로 주입
- `AUDIT_ARCHIVE_POLL_INTERVAL=PT10S`

객체 키는 `<sequence>-<eventHash>.json`이며 `Idempotency-Key`에도 같은 event hash를 사용한다. 보관 객체에는 원본 이벤트와 체인 필드가 모두 포함되므로 DB 사본과 독립적으로 연속성과 내용을 검증할 수 있다. endpoint와 token은 감사 payload나 로그에 기록하지 않는다.
