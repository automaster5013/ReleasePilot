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

후속 [S3 SDK 경계 실패·복구](audit-s3-recovery.md)는 실제 AWS 호출 없이 조건부 쓰기와 충돌 시 미완료 상태 유지를 검사한다.

## 배치 실패 격리 후속 검증

Worker의 배치 첫 항목에서 sink 예외 또는 이벤트 누락이 발생해도 다음 정상 항목을 DELIVERED로 처리하는지 검사한다. 실패 항목은 고정 오류/PENDING/1회 재시도를 유지하고 정상 항목은 오류 없이 완료 시각을 기록한다. 빈 due batch에서는 이벤트 조회나 sink 호출을 하지 않는지도 확인한다.

추가 3개는 repository/sink mock을 쓰는 제어 흐름 검증이다. DB 예외에 따른 transaction rollback-only, 프로세스 중단, 복수 Worker의 중복 전송을 격리·복구하는 검증은 아니다. 운영 코드는 변경하지 않는다.

로컬 결과: Worker 테스트 15개 및 서버 전체 verify 183개가 실패·오류·건너뜀 없이 통과했다.

## 배치 sink 실패 격리 실제 DB 검증

`AuditStorageIntegrationTests.archiveBatchSinkFailureDoesNotPreventNextDeliveryDatabaseRoundTrip`는 실제 Worker/repository와 mock sink를 사용한다. 두 합성 이벤트의 due 시각을 다르게 설정해 실제 DB 조회 순서를 고정하고, 첫 이벤트에만 sink 예외를 주입한다. flush·clear·재조회 후 첫 항목의 PENDING/고정 오류/attempts=1/2초 재시도/완료 시각 없음과 다음 항목의 DELIVERED/오류 없음/attempts=0/완료 시각을 각각 확인한다. sink 호출 순서와 감사 체인의 유효성·이벤트 수 증가·head hash·첫 이벤트 hash 유지도 검사한다.

H2와 소유권 확인 임시 MySQL에서 같은 테스트가 통과했다. 서버 전체 verify는 184개, MySQL 통합은 22개(분석 6/감사 저장 14/동시성 2)이며 실패·오류·건너뜀은 없다. MySQL helper의 Flyway migration 22개·JSON 컬럼 타입 5개 검사와 UUID label/기록된 컨테이너 ID 기반 컨테이너·익명 볼륨 cleanup도 성공했다. 실행 중 임시 컨테이너 `43202e572c6c`의 loopback 바인딩을 확인했고 종료 후 해당 label의 컨테이너가 남아 있지 않음을 확인했다. helper 실행 계정은 임시 DB의 `analysis_test`다.

```powershell
cd C:\ReleasePilot\apps\control-plane
./mvnw.cmd --batch-mode verify
cd C:\ReleasePilot
docker pull mysql:8.4
python scripts/analysis_mysql_integration.py
apps/analysis-worker/.venv/Scripts/ruff.exe check scripts/analysis_mysql_integration.py
```

새 시나리오는 테스트 트랜잭션 내 SQL 저장·재조회 후 롤백한다. commit 이후 재시작 내구성, DB 장애로 인한 rollback-only 격리, 프로세스 중단, 복수 Worker 중복 전송은 미검증이다. 외래 키를 우회하는 이벤트 누락 재현은 하지 않았다. 기존 제어 흐름 mock/HTTP loopback/S3 SDK mock 검증과 이번 실제 DB 검증을 구분하며 실제 AWS/S3/외부 HTTP에는 전송하거나 장애를 주입하지 않았다. 운영 데이터·전달 상태·기존 last_error는 수정하지 않았고 새 운영 배포는 없다. 운영 실제 릴리스 데이터 부재로 전체 운영 E2E는 계속 미검증이다. SSO/GitHub Checks 보류를 유지한다.

## commit 이후 별도 트랜잭션 복구 검증

`AuditConcurrencyIntegrationTests.archiveBatchFailureCommitsAndNewWorkerRecoversInSeparateTransaction`를 추가했다. 실제 `TransactionTemplate`으로 합성 이벤트·due 순서를 commit한 뒤, mock sink가 첫 항목에만 예외를 발생시키는 Worker 배치를 별도 트랜잭션에서 실행하고 commit한다. 다음 트랜잭션에서 실패 항목의 PENDING/고정 오류/attempts=1/재시도 시각/완료 시각 없음과 성공 항목의 DELIVERED/attempts=0/오류 없음/완료 시각을 확인한다.

새 Worker와 새 mock sink를 만들고 2초 뒤 재시도를 다른 트랜잭션에서 commit한다. 다시 별도 트랜잭션에서 실패 항목의 DELIVERED/오류 제거/attempts=1/새 완료 시각과 기존 성공 항목의 상태·완료 시각 유지를 확인한다. 복구 sink는 실패했던 이벤트만 정확히 한 번 받고 추가 호출이 없으며, 실패 commit 후와 복구 commit 후 모두 감사 체인 유효성·이벤트 수·head hash가 유지된다. 첫 이벤트 hash도 복구 후 원본과 일치한다.

결과: H2 전체 verify 185개, MySQL 통합 23개(분석 6/감사 저장 14/트랜잭션·동시성 3)가 실패·오류·건너뜀 없이 통과했다. MySQL Flyway 22개·JSON 타입 5개 검사, helper의 UUID label/기록된 ID 기반 컨테이너·익명 볼륨 cleanup과 종료 후 잔여 컨테이너 없음 확인도 성공했다. 실행 중 소유 임시 컨테이너 `248cd2cc1ca3`는 loopback에만 바인딩됐다. 실행 명령은 위 실제 DB 검증과 동일하며 Ruff도 통과했다.

이 후속 테스트는 롤백하지 않고 임시 DB에 commit하며, DB 자체가 검증 종료 후 폐기된다. 같은 JVM에서 새 Worker와 새 트랜잭션을 사용하는 검증이므로 실제 프로세스/DB 재시작·강제 종료 내구성, DB rollback-only 격리, 복수 Worker 중복 전송, 실제 외부 저장소 전달을 증명하지 않는다. scheduler Spring proxy 자동 실행도 검사하지 않는다. 운영 데이터·전달 상태·기존 last_error 수정, 외래 키 우회, 실제 AWS/S3/외부 HTTP 호출과 새 운영 배포는 없고, 운영 전체 릴리스 E2E 및 SSO/GitHub Checks 보류를 유지한다.

## 배치 트랜잭션 롤백 후 재전송 경계

이후 [실제 DB·loopback HTTP 배치 commit 통합 검증](audit-http-recovery.md#실제-dbhttp-배치-commit-통합-검증)에서 repository와 sink 모두 실제 구현을 사용하는 실패·복구 시나리오를 추가했다.

`AuditConcurrencyIntegrationTests.archiveBatchRollbackLeavesBothEventsDueForRedelivery`는 실제 DB에 합성 이벤트와 due 순서를 commit한 뒤 mock sink에서 첫 항목만 실패시키고 다음 항목은 성공시킨다. 배치 전달 상태를 flush해 SQL 저장을 실행하고, 트랜잭션 안에서 실패 PENDING/attempts=1/고정 오류와 성공 DELIVERED를 확인한 뒤 `setRollbackOnly()`로 전체 롤백한다.

다음 별도 트랜잭션에서는 두 항목 모두 원래 PENDING/attempts=0/오류·완료 시각 없음/원래 due 시각으로 조회된다. 즉 sink 호출이 성공해도 그 성공 상태는 배치 DB 트랜잭션과 함께 롤백된다. 첫 항목의 mock 실패를 해제하고 같은 시각에 새 배치를 commit하면 두 항목이 모두 DELIVERED가 된다. 두 이벤트의 sink 호출은 각각 2회이며, 앞서 성공한 항목도 재전송 대상이 됨을 확인한다. 실패 횟수도 롤백되므로 복구 후 attempts는 0이다. 감사 체인은 롤백 후 유효하고 재전송 commit 후 이벤트 수·head hash·첫 이벤트 hash가 유지된다.

결과: H2 전체 verify 186개, 실제 MySQL 통합 24개(분석 6/감사 저장 14/트랜잭션·동시성 4)가 실패·오류·건너뜀 없이 통과했다. Flyway migration 22개·JSON 타입 5개 검사와 Ruff도 통과했다. 소유 임시 컨테이너 `71a0925268d0`는 loopback에만 바인딩됐고, helper의 UUID label/기록된 컨테이너 ID 확인 후 컨테이너·익명 볼륨 cleanup과 잔여 컨테이너 없음 확인을 완료했다. 실행 명령은 위 실제 DB 검증과 같다.

이는 명시적으로 rollback-only를 설정한 실제 트랜잭션 검증이며 실제 SQL 오류·DB 연결 단절·commit 실패·프로세스 강제 종료를 주입하지 않는다. 항목별 독립 commit이나 DB 장애 격리 기능을 추가하지 않았고, DB 상태와 외부 sink 호출 사이의 exactly-once 전달을 보장하지 않는다. mock 호출 횟수를 검증했으므로 원격 저장소의 실제 중복 저장·중복 제거는 미검증이다. 실제 HTTP loopback 및 S3 SDK mock 검증과 구분한다. 운영 데이터·전달 상태·기존 last_error, 외래 키, 실제 AWS/S3/외부 HTTP는 변경·호출하지 않았다. 새 운영 배포 없이 운영 E2E/실제 재시작/복수 Worker 검증 및 SSO/GitHub Checks 보류를 유지한다.
