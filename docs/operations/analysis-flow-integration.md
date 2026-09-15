# 분석 결과 → Outbox → 제어 → 감사 통합 검증

2026-09-15

`AnalysisFlowIntegrationTests`는 Spring Boot의 실제 AnalysisJobProcessor, OutboxCommandProcessor, StartRolloutCommandHandler와 JPA 저장소를 사용한다. 별도 `analysis-flow` H2 메모리 DB에 합성 카탈로그/정책/릴리스/실행/분석 작업을 저장한다. 외부 Worker, Kubernetes gateway, secret resolver만 mock으로 대체한다. 운영 데이터와 자격 증명을 읽거나 변경하지 않는다.

## 검증 시나리오 6개

- PASS → 분석 완료 → PROMOTE Outbox → 단계 PASSED → 성공 감사
- FAIL → 분석 완료 → ABORT Outbox → 실행 ABORTED/단계 FAILED → 감사
- INCONCLUSIVE(최대 시도) → PAUSE Outbox → 실행 PAUSED → 감사
- PASS 예약 후 Prometheus 비활성화 → Outbox FAILED, 외부 제어/단계 통과/성공 감사 없음
- 설정된 비밀 값 누락 → Worker 호출 없음, SECRET_UNAVAILABLE → PAUSE 실행
- Worker 예외 → 고정 PROMETHEUS_UNAVAILABLE/빈 근거 → PAUSE 실행, 예외 원문은 감사에 없음

정상 경로는 분석/명령을 두 번째 처리해도 다시 실행되지 않는 것을 검사한다. 분석 결과, Outbox 상태/시도 횟수, 실행/단계 상태와 감사는 다시 DB에서 읽어 확인한다.

로컬 통합 시나리오 6개 및 서버 전체 `verify`의 144개 테스트가 실패·오류·건너뜀 없이 통과했다.

## 통합 검증에서 확인한 JSON 매핑 문제

기존 JSON 컬럼은 SQL DDL만 `json`으로 지정하고 Java String의 JDBC 타입을 명시하지 않았다. H2 저장 후 정책 객체가 JSON 문자열 값으로 감싸져 읽혔으며, 요청 구성이 INCONCLUSIVE로 내려가고 Outbox JSON 파싱도 실패했다. 실제 저장소 mock 테스트에서는 드러나지 않았다.

PolicyVersion/PolicySnapshot definition, AnalysisJob evidence, OutboxCommand payload, AuditEvent payload, EnvironmentValidationResult details에 `@JdbcTypeCode(SqlTypes.JSON)`을 명시했다. 데이터 모델/SQL 컬럼이나 API 계약을 바꾸지 않고 JSON 바인딩을 지정한다. [Hibernate JSON 매핑 문서](https://docs.jboss.org/hibernate/orm/current/userguide/html_single/Hibernate_User_Guide.html)와 [H2 JSON 타입 문서](https://h2database.com/html/datatypes.html)를 확인했다. 기존 운영 DB 기록을 조회·변환하지 않았다.

## 범위와 실행

```powershell
cd apps/control-plane
./mvnw.cmd --batch-mode -Dtest=AnalysisFlowIntegrationTests test
./mvnw.cmd --batch-mode verify
```

이 검증은 backend 서비스/DB/Outbox 통합 테스트이며 전체 운영 E2E가 아니다. H2는 MySQL의 migration/JSON/locking 동작을 모두 대체하지 않는다. 실제 Prometheus 판정, Kubernetes 네트워크 mutation, 브라우저 전체 흐름은 이 테스트에서 검증하지 않는다. 공개 배포는 기존 v0.52.0이며 이번 매핑 보강은 아직 배포하지 않았다.
