# 감사 아카이브 실패·재시도 안전성

2026-09-15

`AuditArchiveWorker`는 아카이브 예외의 메시지나 클래스 이름을 전달 상태에 저장하지 않고 고정 코드 `AUDIT_ARCHIVE_UNAVAILABLE`만 기록한다. 예외에 토큰·내부 URL 등이 포함돼도 `last_error`에 복사하지 않는다. 원본 감사 이벤트는 변경하지 않는다. 이벤트 조회 실패도 같은 코드로 PENDING 재시도한다.

재시도 간격은 2, 4, 8, …, 128, 256초를 거쳐 9번째 실패부터 300초로 제한된다. 기존 구현은 shift 상한 8로 256초에서 멈췄으며, 5분 상한에 도달하도록 보정했다. 재시도 횟수 자체의 종료 제한은 추가하지 않는다.

`AuditArchiveWorkerTests`의 12개 사례는 민감한 합성 예외/메시지 없는 예외/이벤트 누락의 고정 코드, PENDING 유지, 복구 후 DELIVERED·오류 제거, due PENDING 20개 batch 조회 조건, 재시도 지연 경계값을 검사한다. repository/sink는 mock이고 엔티티는 실제 객체다. 이 검증은 실제 S3/HTTP 전달이나 복수 Worker의 중복 전송 방지를 증명하지 않는다.

```powershell
cd apps/control-plane
./mvnw.cmd --batch-mode -Dtest=AuditArchiveWorkerTests test
./mvnw.cmd --batch-mode verify
```

운영 전달 상태나 기존 last_error를 변경·정리하지 않았다. 이후 [v0.55.0 배포 검증](audit-archive-v0.55.0.md)을 완료했다.

로컬 검증 결과: 추가한 12개 및 서버 전체 verify 169개가 실패·오류·건너뜀 없이 통과했다.

## 실제 DB 왕복 후속 검증

`AuditStorageIntegrationTests`에 실제 repository/Worker와 mock sink를 사용하는 2개 시나리오를 추가했다. 실패 후 last_error/attempts/available_at/PENDING을 flush·clear·재조회하고, 복구 후 DELIVERED/delivered_at/오류 제거와 감사 해시 유지도 확인한다. 미래 available_at 항목이 전송 대상에 포함되지 않는지 실제 DB 조회로 검사한다.

테스트는 외부 전송 없이 테스트 트랜잭션 안에서 진행하고 모두 롤백한다. 이는 저장·조회와 선택 조건 검증이지 프로세스 재시작 내구성이나 scheduler bean 자동 생성/실제 외부 저장소 전달 검증이 아니다. 기존 MySQL helper와 CI가 동일 테스트를 실행한다.

검증 결과: H2 서버 전체 verify 171개 및 실제 MySQL 8.4.11 통합 21개(분석 6/감사 저장 13/동시성 2)가 모두 통과했다. Flyway migration 22개·JSON 타입 5개 검사와 소유권 확인 후 컨테이너/익명 볼륨 cleanup도 성공했다.

이후 [실제 loopback HTTP 장애·복구](audit-http-recovery.md) 검증을 추가했다. 운영 외부 저장소 검증과 구분한다.
