# 감사 기록 DB 무결성 검증

2026-09-15: `AuditStorageIntegrationTests`는 실제 Spring 서비스/JPA를 사용해 저장 후 재조회한 감사 해시 체인을 검사한다. 기본 H2와 임시 MySQL 8.4에서 실행한다.

검증 사례 5개는 한글/중첩 JSON/마이크로초 시각의 해시 유지, 의미가 같은 JSON 키 순서·공백 변경 허용, 내용·이전 해시·순번 변경 탐지다. archive delivery 생성도 확인한다. 테스트 트랜잭션은 모두 롤백한다. 이는 DB 왕복 검증이며 재시작 이후 내구성, 전체 삭제 탐지, 외부 아카이브 전달 성공을 증명하지 않는다.

실제 MySQL에서 기존 분석 감사의 해시 불일치를 재현했다. 해시는 시각을 마이크로초로 절삭하지만 엔티티는 나노초를 유지했다. `AuditEvent` 생성 시각을 해시 계산과 동일한 마이크로초 정밀도로 정규화해 DB 저장의 정밀도 변환과 불일치를 방지했다. 기존 운영 기록은 조회하거나 변경하지 않았으며 이미 저장된 불일치 기록을 복구하는 작업은 포함하지 않는다.

```powershell
docker pull mysql:8.4
python scripts/analysis_mysql_integration.py
```

기존 임시 DB helper와 CI 실행 단계가 분석 6개 및 감사 5개를 함께 실행한다. 로컬 MySQL 8.4.11에서 총 11개 통과, Flyway migration 22개 및 JSON 타입 5개 검사 통과, 소유권 확인 후 임시 컨테이너와 익명 볼륨 정리를 확인했다. 기본 H2 서버 전체 `verify`도 149개 모두 통과했다. 이후 [v0.53.0 배포 검증](storage-hardening-v0.53.0.md)을 완료했다.

## 체인 끝값 후속 보강

추가한 6개 시나리오는 마지막 이벤트 삭제, head 부재, head 해시/순번 변경, eventHash 제거, 세 체인 필드가 모두 null인 legacy 제외를 검증한다. verifier는 chain 후보를 부분 null 필드까지 조회하고 head 끝값도 비교한다. 실패 시 verifiedEvents는 성공적으로 검증한 수만 집계한다. API 필드는 그대로이며 head 불일치에서는 failedEventId가 null이다.

H2 전체 verify 155개 및 실제 MySQL 분석 6개/감사 11개 총 17개가 모두 통과했다. Flyway 22개/JSON 타입 검사 및 소유권 cleanup도 통과했다. 변조·삭제는 임시 테스트 트랜잭션에만 한정되고 롤백한다. 공개 이미지는 아직 v0.53.0이다. 잠금 경합의 부하/동시성 스트레스 테스트와 외부 아카이브 검증은 포함하지 않는다.
