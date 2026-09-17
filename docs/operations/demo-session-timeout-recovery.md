# 공개 데모 세션 시작 timeout 복구

## 적용 범위

공개 데모 세션 생성 POST에 공통 `fetchWithTimeout`의 15초 제한을 적용했다. 응답이 멈추면 요청을 중단하고 재시도 가능한 한국어 오류를 알린 뒤 데모 시작 잠금과 busy 상태를 해제한다. 오류를 발생시킨 데모 제어로 포커스를 복원하므로 사용자는 즉시 다시 시도할 수 있다.

## 회귀 검증

- 첫 데모 세션 생성 응답을 정지시켜 제어가 잠겼다가 timeout 후 복구되고 VIEWER 세션으로 전환되지 않는지 확인했다.
- 같은 제어의 두 번째 시도가 성공하고 CSRF로 보호된 POST가 정확히 두 번만 전송되는지 검증했다.
- 대상 시나리오는 10 worker 병렬 반복 10회에 통과했다.
- 단위 테스트 37개, lint, typecheck, production build, Chromium 전체 193개와 프로젝트 로그 관리 계약이 통과했다.
- CI `35272176230`(#293)은 5분 56초에 성공했다. Chromium 193개와 Firefox/WebKit 접근성 각 73개를 합쳐 브라우저 테스트 339개를 검증했다.

## 운영 반영

Docker Hub CD `35272176415`(#86)은 변경된 web-console만 게시하고 12분 17초에 성공했다. GitOps commit `dca9cea`는 web-console을 `sha256:e87813cb1bd39eb6408028457677c966268d60500ccee499c36e31899eaacb68`로 갱신했다.

Canary는 자동 20% 관찰 단계를 통과한 뒤 50%에서 신규·기존 Pod Ready, 재시작 0, 정확한 digest와 공개 root·`/health` HTTPS 200을 확인하고 100%로 수동 승격했다. 최종 web-console Rollout은 새 digest로 2/2 updated·ready, Healthy이며 최근 10분 오류 표본은 0건이다. 전체 운영 Pod 재시작은 0이고 sample-checkout은 1/1 Healthy, 네 Argo CD Application은 Synced/Healthy다. root의 CSP와 HSTS, health의 HSTS도 유지된다.

요청이 서버에 도달한 뒤 응답만 유실되면 재시도가 같은 브라우저 세션을 다시 설정하고 IP 기반 데모 rate limit에도 포함될 수 있다. 다중 탭의 동시 생성은 서버 rate limit으로 제한하지만 탭 간 단일 실행 잠금은 제공하지 않는다.
