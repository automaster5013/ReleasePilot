# 외부 연결 변경 timeout 복구

## 적용 범위

Kubernetes·Prometheus 연결 등록, 편집, 활성화·비활성화 요청에 공통 `fetchWithTimeout`의 15초 제한을 적용했다. 응답이 멈추면 요청을 중단하고 재시도 가능한 한국어 오류를 assertive 상태로 알린 뒤, 등록 또는 변경 공유 잠금과 busy 상태를 해제한다. 오류를 발생시킨 제출·변경 제어로 포커스를 복원하므로 운영자는 입력을 잃지 않고 즉시 다시 시도할 수 있다.

등록 두 경로와 편집·상태 변경 네 경로는 각각 기존 공유 동기 잠금을 유지한다. 같은 이벤트 루프의 중복 요청과 서로 다른 연결 사이의 교차 mutation은 계속 차단된다.

## 회귀 검증

- Kubernetes 등록과 Prometheus 재활성화의 첫 응답에 timeout 오류를 주입해 관련 제어가 잠겼다가 오류 후 복구되는지 확인했다.
- 입력을 유지한 두 번째 등록·상태 변경이 성공하고 각 시나리오에 정확히 두 번의 동일 mutation만 기록되는지 검증했다.
- AbortController의 실제 timeout 변환은 단위 계약으로 검증하고 UI 복구는 결정적 timeout 오류 주입으로 검증했다.
- 두 대상 시나리오는 각각 10회, 총 20개를 10 worker로 병렬 반복해 통과했다.
- 단위 테스트 37개, lint, typecheck, production build, Chromium 전체 190개와 프로젝트 로그 관리 계약이 통과했다.
- CI `35231253772`(#287)는 6분 10초에 성공했다. Chromium 190개와 Firefox/WebKit 접근성 각 73개를 합쳐 브라우저 테스트 336개를 검증했다.

## 운영 반영

Docker Hub CD `35231254809`(#83)은 변경된 web-console만 게시하고 12분 35초에 성공했다. GitOps commit `285ac3d`은 web-console을 `sha256:b95d4f239e384533aa6eaa01c49fef6a0e67715dc32997f5973be922647b8459`로 갱신했다.

Canary는 자동 20% 관찰 단계를 통과한 뒤 50%에서 신규·기존 Pod Ready, 재시작 0, 공개 root·`/health` HTTPS 200을 확인하고 100%로 수동 승격했다. 최종 web-console Rollout은 새 digest로 2/2 updated·ready, Healthy이고 Argo CD는 GitOps revision `285ac3d`에서 Synced/Healthy다. root의 CSP와 HSTS, health의 HSTS도 유지된다.
