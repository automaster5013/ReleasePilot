# 감사 이벤트 무결성과 외부 보관

ReleasePilot은 새 감사 이벤트마다 단조 증가하는 `chainSequence`, 직전 이벤트의 `previousHash`, 현재 이벤트의 `eventHash`를 저장한다. 해시는 이벤트 식별자, aggregate, actor, 발생 시각, correlation ID와 원본 JSON payload를 순서가 고정된 문자열로 직렬화한 뒤 SHA-256으로 계산한다.

`audit_chain_head` 행을 비관적 잠금으로 획득하므로 동시에 생성되는 이벤트도 하나의 전역 체인에 정확히 한 번 연결된다. 감사 이벤트와 `audit_archive_deliveries` 레코드는 업무 변경과 동일한 DB 트랜잭션에 기록된다. 외부 저장소 장애는 업무 트랜잭션을 되돌리지 않고 전달 상태를 `PENDING`으로 유지하며 최대 5분까지 지수 백오프로 재시도한다.

## 검증

OPERATOR는 `GET /api/v1/audit-events/verify`를 호출해 DB에 저장된 해시 체인을 다시 계산할 수 있다. `valid=false`이면 `failedEventId`가 최초 불일치 이벤트를 가리킨다. 끝값 불일치나 head 부재는 해당 이벤트를 식별할 수 없어 `failedEventId=null`이다. `verifiedEvents`는 불일치 이전에 검증을 완료한 이벤트 수다. V20 이전의 legacy 이벤트는 세 체인 필드가 모두 null이므로 제외되고, 일부 필드만 null인 기록은 실패한다. V20 이후 체인은 genesis hash부터 시작한다.

검증은 기록 생성과 같은 head 비관적 잠금을 사용해 이벤트 목록과 끝값을 비교한다. 마지막 이벤트 삭제와 head 순번/해시 불일치도 거부한다. 전체 체인과 head를 함께 일관되게 재작성하는 공격, 모든 체인 필드가 지워져 legacy와 구분 불가능한 기록, 외부 보관본 무결성까지 증명하지 않는다. 전체 목록 검증 동안 감사 기록 생성이 대기할 수 있으므로 대규모 DB에서는 운영 부하를 고려해야 한다. 이 후속 보강은 v0.53.0 이후 소스 변경이며 아직 공개 이미지에 배포하지 않았다.

`aggregateType=ENVIRONMENT&aggregateId=<uuid>` 조회는 OPERATOR에게만 허용한다. 수동 Environment 재검증은
USER actor와 `MANUAL` trigger로 기록되고, 정기 재검증은 SYSTEM actor와 `SCHEDULED` trigger를 유지한다.

## 외부 보관 설정

외부 WORM 저장소나 보관 게이트웨이가 HTTPS PUT과 멱등 키를 지원할 때 다음 환경 변수를 설정한다.

- `AUDIT_ARCHIVE_ENABLED=true`
- `AUDIT_ARCHIVE_ENDPOINT=https://archive.example/releasepilot/`
- `AUDIT_ARCHIVE_TOKEN`은 Kubernetes Secret 등 런타임 secret으로 주입
- `AUDIT_ARCHIVE_POLL_INTERVAL=PT10S`

객체 키는 `<sequence>-<eventHash>.json`이며 `Idempotency-Key`에도 같은 event hash를 사용한다. 보관 객체에는 원본 이벤트와 체인 필드가 모두 포함되므로 DB 사본과 독립적으로 연속성과 내용을 검증할 수 있다. endpoint와 token은 감사 payload나 로그에 기록하지 않는다.

### AWS 운영 환경

AWS overlay는 `AUDIT_ARCHIVE_PROVIDER=s3`를 사용한다. Terraform은 공개 접근을 전부 차단하고 AES-256 암호화, Versioning, 30일 COMPLIANCE Object Lock을 적용한 버킷을 생성한다. Control Plane ServiceAccount에는 EKS Pod Identity로 `audit-events/*`의 `s3:PutObject`와 bucket 위치 조회만 허용하며 읽기·덮어쓰기·삭제 권한은 부여하지 않는다. 객체 생성은 `If-None-Match: *` 조건을 사용해 같은 체인 항목의 덮어쓰기를 거부한다.

운영 확인:

```powershell
aws s3api get-object-lock-configuration --bucket releasepilot-demo-audit-327771416502
aws eks list-pod-identity-associations --cluster-name releasepilot-demo
aws s3api list-objects-v2 --bucket releasepilot-demo-audit-327771416502 --prefix audit-events/
```
