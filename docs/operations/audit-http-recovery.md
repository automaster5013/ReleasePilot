# HTTP 아카이브 장애·복구 로컬 검증

2026-09-15

`HttpAuditArchiveSinkTests`에 실제 HTTP sink와 Worker를 연결한 429/500/503 장애·복구 3개 시나리오를 추가했다. 테스트 서버는 `127.0.0.1` 임의 포트에만 bind하고 finally에서 종료한다. 운영 URL/자격 증명은 사용하지 않는다.

첫 응답 실패 후 PENDING·고정 오류 코드·2초 재시도 시각, 두 번째 201 응답 후 DELIVERED·오류 제거·완료 시각을 확인한다. 실제 두 PUT의 경로/멱등 키/JSON 본문이 동일하고, 합성 인증 토큰이나 오류 응답의 합성 비밀값이 본문/전달 오류에 들어가지 않는지도 검사한다.

repository는 mock이며 전송은 실제 loopback HTTP다. 이는 앞선 DB 왕복 테스트와 별개다. 실제 S3, 운영 HTTP, scheduler 자동 실행, 복수 Worker, 네트워크 단절/timeout, 서버가 성공 저장 후 응답을 잃는 상황은 검증하지 않는다. 같은 키 재전송은 확인하지만 원격 저장소의 중복 제거 구현을 증명하지 않는다.

```powershell
cd apps/control-plane
./mvnw.cmd --batch-mode -Dtest=HttpAuditArchiveSinkTests test
./mvnw.cmd --batch-mode verify
```

운영 코드는 변경하지 않았으며 공개 배포는 기존 v0.55.0을 유지한다.

로컬 결과: HTTP 테스트 4개(기존 1/추가 3), 서버 전체 verify 174개가 실패·오류·건너뜀 없이 통과했다.

## 전송 중단·시간 초과 후속 검증

응답을 지연하는 loopback 서버로 실제 10초 요청 timeout을 발생시켜 `HttpTimeoutException` 원인과 PENDING/고정 오류/재시도 시각/미완료 상태를 검사한다. 별도 스레드 중단 사례는 HTTP 전송 후 interrupt flag가 보존되고 완료로 처리되지 않는지 확인한다. 서버의 지연 대기는 20초 상한과 finally 해제 신호를 사용한다. 중단 flag는 테스트 종료 시 정리한다.

repository는 여전히 mock이다. 원격 서버에 실제 객체가 저장됐는지, 응답을 잃은 경우의 중복 제거, 네트워크 전체 장애 복구는 증명하지 않는다. 운영 timeout 설정이나 코드는 변경하지 않았다.

후속 로컬 결과: HTTP 테스트 6개 및 서버 전체 verify 180개가 실패·오류·건너뜀 없이 통과했다.

## 실제 DB·HTTP 배치 commit 통합 검증

`AuditConcurrencyIntegrationTests.committedHttpBatchFailureRecoversOnlyFailedEventWithSameEnvelope`는 실제 repository/Worker/HttpAuditArchiveSink와 loopback HTTP 서버를 결합한다. 두 합성 이벤트와 서로 다른 due 시각을 commit하고, 첫 이벤트의 멱등 키에만 최초 503을 반환하며 다음 이벤트에는 201을 반환한다. 배치를 commit한 뒤 별도 트랜잭션에서 첫 항목의 PENDING/고정 오류/attempts=1/2초 재시도/완료 시각 없음과 다음 항목의 DELIVERED/오류 없음/attempts=0/완료 시각을 확인한다.

새 HTTP sink/Worker로 재시도 시각에 배치를 commit하고, 다시 별도 트랜잭션에서 첫 항목의 DELIVERED/오류 제거/attempts=1/복구 완료 시각과 기존 성공 항목의 상태·완료 시각 유지를 확인한다. 두 대상의 실제 요청 순서는 첫 이벤트→다음 이벤트→첫 이벤트다. 실패 이벤트의 첫 요청과 재시도는 PUT 경로·멱등 키·본문이 같고, 경로의 순번/hash와 JSON eventHash가 DB 기록에 일치한다. 요청 본문에 합성 인증 토큰·응답 비밀이 포함되지 않는다. 실패 commit 후 감사 체인은 유효하며 복구 commit 후 이벤트 수와 head hash도 유지된다.

결과: H2 전체 verify 187개, 실제 MySQL 통합 25개(분석 6/감사 저장 14/트랜잭션·동시성 5)가 실패·오류·건너뜀 없이 통과했다. Flyway migration 22개·JSON 타입 5개 검사와 Ruff도 통과했다. 소유 임시 MySQL 컨테이너 `f13d5a6260f4`는 loopback에만 바인딩됐고, UUID label/기록된 ID 확인 후 컨테이너·익명 볼륨 cleanup 및 잔여 컨테이너 없음 확인을 완료했다. HTTP 서버는 finally에서 stop한다.

```powershell
cd C:\ReleasePilot\apps\control-plane
./mvnw.cmd --batch-mode verify
cd C:\ReleasePilot
docker pull mysql:8.4
python scripts/analysis_mysql_integration.py
apps/analysis-worker/.venv/Scripts/ruff.exe check scripts/analysis_mysql_integration.py
```

이번 시나리오의 repository와 sink는 mock이 아니며 실제 DB commit과 loopback HTTP 응답을 검증한다. 앞선 HTTP 단위 테스트의 repository mock 및 S3 SDK mock 검증과 구분한다. 테스트 서버는 원격 저장소의 영구 저장·중복 제거를 구현하지 않는다. 실제 외부 HTTP/AWS/S3 전달, 응답 유실/네트워크 장애, 프로세스·DB 재시작, DB commit 실패, 복수 Worker 중복 전송과 scheduler proxy 자동 실행은 미검증이다. 합성 데이터만 임시 DB에 commit하고 DB를 폐기하며 운영 데이터·전달 상태·기존 last_error와 외래 키는 변경하지 않았다. 새 운영 배포 없이 운영 전체 E2E 및 SSO/GitHub Checks 보류를 유지한다.

## HTTP 수신 후 DB 롤백·멱등 재수신

`AuditConcurrencyIntegrationTests.httpReceiverDeduplicatesRedeliveryAfterDatabaseRollback`는 실제 repository/Worker/HTTP sink와 메모리 객체 저장 규칙을 가진 loopback 수신 서버를 사용한다. 서버는 eventHash 멱등 키별 첫 요청을 한 객체로 저장하고 201을 반환하며 동일 요청의 재수신에는 객체를 추가하지 않고 200을 반환한다. 다른 envelope는 409로 거부하도록 테스트 수신 규칙을 구성했지만 충돌 분기는 이번 시나리오에서 실행하지 않는다.

합성 감사 이벤트를 DB에 commit한 뒤 첫 HTTP 수신을 완료하고 DELIVERED를 SQL로 flush한다. 배치 트랜잭션을 명시적으로 rollback-only 처리하면 다음 트랜잭션에서 PENDING/attempts=0/오류·완료 시각 없음으로 조회되지만 수신 서버에는 객체가 남는다. 새 sink/Worker가 1초 뒤 동일 PUT 경로·멱등 키·본문을 다시 보내 200을 받은 후 DELIVERED를 commit한다. 실제 대상 요청 2회와 수신 객체 1개, 복구 완료 시각, 감사 체인 유효성·이벤트 수·head hash 유지를 확인한다. commit 이후 세 번째 tick에서는 HTTP 호출이 추가되지 않는다.

H2 전체 verify 188개 및 MySQL 통합 26개(분석 6/감사 저장 14/트랜잭션·동시성 6)가 실패·오류·건너뜀 없이 통과했다. 위 실행 명령의 Flyway migration 22개·JSON 타입 5개 검사와 Ruff도 통과했다. 소유 임시 MySQL 컨테이너 `59479c7d05a3`의 loopback 바인딩을 확인하고, UUID label/기록된 컨테이너 ID 검증 후 컨테이너·익명 볼륨 cleanup과 잔여 컨테이너 없음 확인을 완료했다. HTTP 서버는 finally에서 stop한다.

repository와 sink는 실제 구현이며 수신 저장은 테스트 서버의 메모리 map이다. 실제 외부 저장소의 영구 저장·중복 제거·충돌 처리, 응답 유실, 실제 DB 장애/commit 실패, 프로세스·DB 재시작/강제 종료와 복수 Worker를 증명하지 않는다. 수신 규칙이 동일 재시도를 성공으로 인정해야 DB 롤백 이후 재전송을 완료할 수 있다는 통합 계약 검증이다. exactly-once 전달이나 S3 412 복구 기능을 추가하지 않았다. 운영 데이터·전달 상태·기존 last_error 및 외래 키는 수정하지 않았고 실제 외부 HTTP/AWS/S3 호출과 새 운영 배포는 없다. 운영 전체 E2E 및 SSO/GitHub Checks 보류를 유지한다.
