# 외부 연결 검증 timeout 복구

## 적용 범위

Kubernetes와 Prometheus의 개별 연결 검증 및 종류별 일괄 검증 요청에 공통 `fetchWithTimeout`의 15초 제한을 적용했다. 응답이 멈추면 요청을 중단하고 재시도 가능한 한국어 오류를 assertive 상태로 알린 뒤, 공유 검증 잠금과 busy 상태를 해제한다. 오류를 발생시킨 제어로 포커스를 복원하므로 운영자는 같은 검증을 즉시 다시 실행할 수 있다.

세 경로는 기존의 공유 동기 잠금을 유지하므로 개별 검증 중 다른 개별·일괄 검증을 시작할 수 없으며, timeout 뒤 잠금 해제 전까지 교차 요청도 차단된다.

## 회귀 검증

- 첫 Kubernetes 개별 검증에 timeout 오류를 주입해 개별·일괄 제어가 잠겼다가 오류 후 복구되는지 확인했다.
- 동일 입력의 두 번째 검증이 성공하고 정확히 두 번의 검증 mutation만 기록되는지 검증했다.
- AbortController의 실제 timeout 변환은 단위 계약으로 검증하고, UI 복구 시나리오는 지연 route의 AbortSignal 보류 영향을 피하도록 결정적 오류 주입으로 분리했다.
- 대상 시나리오는 10 worker 병렬 반복 10회에 통과했다.
- 단위 테스트 37개, lint, typecheck, production build, Chromium 전체 188개와 프로젝트 로그 관리 계약이 통과했다.
- CI `35227590159`(#285)는 5분 57초에 성공했다. Chromium 188개와 Firefox/WebKit 접근성 각 73개를 합쳐 브라우저 테스트 334개를 검증했다.

## 운영 반영

Docker Hub CD `35227590629`(#82)은 변경된 web-console만 게시하고 10분 46초에 성공했다. GitOps commit `518e6e1`은 web-console을 `sha256:59f52d64612d49a571cbdb57813b46a19d1ee96781f6a62d23ce64afa88b9a47`로 갱신했다.

Canary는 자동 20% 관찰 단계를 통과한 뒤 50%에서 신규·기존 Pod가 각각 Ready이고 재시작 0인 상태와 공개 root·`/health` HTTPS 200을 확인하고 100%로 수동 승격했다. 최종 web-console Rollout은 새 digest로 2/2 updated·ready, Healthy이고 Argo CD는 GitOps revision `518e6e1`에서 Synced/Healthy다. root의 CSP와 HSTS, health의 HSTS도 유지된다.
