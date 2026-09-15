# S3 아카이브 SDK 경계 실패·복구 검증

2026-09-15

`S3AuditArchiveSinkTests`에 Worker와 실제 S3 sink를 연결하고 S3Client/repository만 mock한 4개 사례를 추가했다. 실제 AWS·운영 버킷·자격 증명은 사용하지 않는다.

- 403/500/503 실패 후 복구: PENDING/고정 오류/2초 재시도, 이후 DELIVERED/오류 제거/완료 시각
- 두 요청의 동일 PutObjectRequest와 본문, 결정적 key/event-hash metadata 및 `If-None-Match: *` 유지
- SDK 오류의 합성 비밀값이 전달 오류/보관 본문에 포함되지 않음
- 412 충돌 재시도: 조건부 쓰기 유지, PENDING/미완료 유지, 추가 읽기·삭제 호출 없음

412를 곧바로 전달 성공으로 간주하지 않는다. 성공 저장 후 응답을 잃어도 다음 요청이 412가 될 수 있지만, 기존 객체 내용을 독립적으로 확인하지 않고 동일 객체라고 단정할 수 없다. 현 권한/구현에서는 이 상황을 PENDING으로 계속 재시도한다. 충돌 판별·복구를 완성한 검증이 아니며 권한 확대나 조건부 쓰기 제거도 하지 않는다.

```powershell
cd apps/control-plane
./mvnw.cmd --batch-mode -Dtest=S3AuditArchiveSinkTests test
./mvnw.cmd --batch-mode verify
```

이 검증은 실제 S3 서비스/네트워크·Pod Identity·Object Lock·DB 왕복·복수 Worker를 증명하지 않는다. 운영 코드는 변경하지 않았으며 공개 서비스는 v0.55.0을 유지한다.

로컬 결과: S3 테스트 5개(기존 1/추가 4) 및 서버 전체 verify 178개가 실패·오류·건너뜀 없이 통과했다.
