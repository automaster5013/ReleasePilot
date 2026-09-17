# 환경 재검증 timeout 복구

## 적용 범위

운영자의 환경 재검증 요청에 공통 `fetchWithTimeout`의 15초 제한을 적용했다. 서버 응답이 멈추면 브라우저가 요청을 중단하고 재시도 가능한 한국어 오류를 알린 뒤, 공유 mutation 잠금과 busy 상태를 해제한다. 검증 결과는 먼저 비우므로 timeout이나 실패 뒤에도 릴리스 요청은 fail-closed 상태를 유지한다.

재검증과 릴리스 요청은 기존 공유 잠금을 계속 사용한다. 따라서 재검증 중 릴리스 요청, 릴리스 요청 중 재검증, 같은 이벤트 루프에서 발생하는 중복 동작을 모두 차단한다.

## 회귀 검증

- 정지한 환경 재검증 응답이 15초 뒤 timeout되고 재검증 제어만 다시 활성화되는지 확인했다.
- timeout 뒤에도 릴리스 요청은 비활성 상태이며, 동일 입력의 두 번째 재검증 성공 후에만 활성화되는지 검증했다.
- 대상 시나리오는 10 worker 병렬 반복 10회에 통과했다.
- 단위 테스트 37개, lint, typecheck, production build, Chromium 전체 187개와 프로젝트 로그 관리 계약이 통과했다.
- CI `35224581358`(#283)은 6분 25초에 성공했다. Chromium 187개와 Firefox/WebKit 접근성 각 73개를 합쳐 브라우저 테스트 333개를 검증했다.

## 운영 반영

Docker Hub CD `35224581686`(#81)은 변경된 web-console만 게시하고 9분 30초에 성공했다. GitOps commit `88e048f`은 web-console을 `sha256:9b2b48de5a0e55fa1953c060fe4aa94c65e204bd37c1755d36a76e90ca2d6589`로 갱신했다.

Canary는 자동 20% 관찰 단계를 통과한 뒤 50%에서 신규·기존 Pod가 각각 Ready이고 재시작 0인 상태와 공개 root·`/health` HTTPS 200을 확인하고 100%로 수동 승격했다. 최종 web-console Rollout은 새 digest로 2/2 updated·ready, Healthy이고 Argo CD는 GitOps revision `88e048f`에서 Synced/Healthy다. root의 CSP와 HSTS, health의 HSTS도 유지된다.
