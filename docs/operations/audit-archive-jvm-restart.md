# 감사 아카이브 JVM 종료 후 상태 유지·복구

2026-09-15

검증 결과: H2 전체 verify 190개, MySQL 기존 통합 27개 및 seed/recover 각 1개(총 29개 실행)가 실패·오류·건너뜀 없이 통과했다. seed JVM PID 12548과 recover JVM PID 16864는 서로 달랐고 동일 run UUID를 사용했다. Flyway migration 22개·JSON 타입 5개 검사와 Ruff도 통과했다. 소유 임시 MySQL 컨테이너 `544e9454ee21`의 loopback 바인딩을 확인했고 소유권 기반 컨테이너·익명 볼륨 cleanup 및 잔여 컨테이너 없음 확인을 완료했다.

`AuditArchiveRestartIntegrationTests`와 기존 `scripts/analysis_mysql_integration.py`를 결합해 같은 소유 임시 MySQL을 사용하는 서로 다른 테스트 JVM에서 seed/recover를 실행한다. helper는 seed Maven subprocess가 성공적으로 종료한 뒤 recover subprocess를 시작한다. 각 단계가 JVM PID와 같은 테스트 UUID를 출력한다. 실행 계정은 임시 DB의 `analysis_test`이며 root 접속·운영 인증은 사용하지 않는다.

seed 단계는 UUID로 식별하는 두 합성 감사 이벤트와 due 순서를 commit한다. 실제 Worker/repository와 mock sink로 첫 항목만 실패시키고 다음 항목은 성공시켜 배치를 commit한다. 별도 트랜잭션에서 PENDING/고정 오류/attempts=1/2초 재시도/완료 시각 없음과 DELIVERED/오류 없음/attempts=0/완료 시각을 확인한다.

recover 단계는 새 JVM에서 UUID로 두 이벤트를 DB 재조회하며 생성·복구·덮어쓰기를 먼저 하지 않는다. 저장된 실패·성공 상태, 연속 체인 순번·이전 hash·head hash·체인 유효성을 먼저 확인한다. 재시도 시각 1초 전에는 sink 호출이 없고 상태가 유지되며, 재시도 시각에 실패 항목만 정확히 한 번 전송된다. 복구 commit 후 DELIVERED/오류 제거/attempts=1/새 완료 시각과 기존 성공 항목의 완료 시각 유지를 확인한다. 감사 체인·head hash·검증 이벤트 수도 유지된다.

```powershell
cd C:\ReleasePilot\apps\control-plane
./mvnw.cmd --batch-mode verify
cd C:\ReleasePilot
docker pull mysql:8.4
python scripts/analysis_mysql_integration.py
apps/analysis-worker/.venv/Scripts/ruff.exe check scripts/analysis_mysql_integration.py
```

phase 환경 변수가 없는 전체 verify에서는 별도 H2 메모리 DB에서 seed/recover를 같은 JVM 안에서 수행한다. 이는 H2 commit/재조회 검증이며 H2 파일 저장 또는 JVM 재시작 검증이 아니다. MySQL helper는 기존 27개 통합 검증 후 seed 1개와 recover 1개를 두 subprocess에서 실행한다. helper가 외부에서 상속한 phase/UUID 환경 변수를 제거하고 소유 UUID를 직접 설정한다. 기존 CI가 같은 helper를 실행하므로 재시작 검증도 포함된다.

검증 종료 시 helper가 기록된 컨테이너 ID와 UUID label 소유권을 다시 확인한 뒤 컨테이너와 익명 볼륨만 정리한다. 합성 이벤트는 폐기되는 임시 DB에만 commit한다.

실제 분리된 테스트 JVM의 정상 종료·재실행 이후 DB 저장 상태를 검증하며, 운영 프로세스의 scheduler Spring proxy 자동 실행을 검사하지 않는다. sink는 mock이고 실제 외부 전송·원격 영구 저장·중복 제거는 미검증이다. MySQL 프로세스/컨테이너 자체 재시작, 강제 종료·전원 장애·미완료 commit, DB 연결 단절과 복수 Worker도 미검증이다. 운영 데이터·전달 상태·기존 last_error·외래 키는 수정하지 않았고 실제 AWS/S3/외부 HTTP 호출이나 새 운영 배포는 없다. 운영 전체 E2E 및 SSO/GitHub Checks 보류를 유지한다.
