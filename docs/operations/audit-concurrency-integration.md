# 감사 기록·검증 동시성 검증

`AuditConcurrencyIntegrationTests`는 실제 Spring 트랜잭션/JPA/head 잠금으로 두 가지 동시성 시나리오를 검증한다. 기본 실행은 전용 H2 DB이고, MySQL helper 실행은 소유권이 확인되는 일회용 DB다. 운영 데이터나 외부 gateway를 사용하지 않는다.

1. 작업자 8개가 동시에 감사 기록을 생성한다. 각각 독립적으로 commit하며, 중복 없는 연속 순번·전체 체인 검증 성공·아카이브 대기 항목 8개 증가를 확인한다.
2. 기록 트랜잭션이 head 잠금을 유지하는 동안 다른 스레드에서 verifier를 호출한다. 완료 대기 200ms가 timeout임을 확인하고 기록을 commit한 뒤, verifier가 새 이벤트 수와 동일한 head 해시를 반환하는지 검사한다.

테스트의 latch 대기와 Future 대기에는 제한 시간을 두고, finally에서 기록 잠금 해제 신호를 보낸다. 반복 가능한 기능 검증이지 처리량/운영 규모의 부하 시험이나 모든 트랜잭션 격리 상황의 증명은 아니다. 첫 head 생성 경쟁은 검사하지 않는다. 운영 Flyway는 genesis head를 미리 생성한다.

```powershell
cd apps/control-plane
./mvnw.cmd --batch-mode -Dtest=AuditConcurrencyIntegrationTests test
```

실제 MySQL 실행은 저장소 루트에서 `docker pull mysql:8.4` 이후 `python scripts/analysis_mysql_integration.py`로 수행한다. 기존 분석/감사 검증과 함께 실행하며 CI에도 같은 helper가 연결돼 있다.

2026-09-15 검증 결과: H2 서버 전체 verify 157개 통과. MySQL 8.4.11에서 전체 통합 19개(분석 6/감사 저장 11/동시성 2)가 서로 다른 임시 DB로 2회 모두 통과했다. 각 실행에서 Flyway 22개 및 JSON 타입 5개 검사와 소유권 확인 cleanup도 성공했다. 이후 [v0.54.0 배포 검증](audit-verifier-v0.54.0.md)을 완료했다.
