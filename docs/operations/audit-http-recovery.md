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
