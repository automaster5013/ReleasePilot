# 릴리스 제어 timeout 복구

## 적용 범위

릴리스 운영 조작(Promote, Pause, Resume, Abort)과 승인 결정(Approve, Reject)에 공통 `fetchWithTimeout`의 15초 제한을 적용했다. 서버 응답이 멈추면 요청을 중단하고 재시도 가능한 한국어 오류를 알린 뒤 `finally`에서 공유 operation 잠금과 busy 상태를 해제한다. 오류를 발생시킨 제어로 포커스를 복원하므로 키보드 사용자도 곧바로 다시 시도할 수 있다.

각 mutation은 기존 CSRF와 idempotency key를 유지한다. timeout 뒤 재시도에는 새 idempotency key를 사용하며, 첫 요청이 서버에 도달한 뒤 응답만 유실된 경우의 중복 효과는 서버 idempotency 계약이 차단한다.

## 회귀 검증

- 정지한 Promote와 Approve 응답이 각각 15초 뒤 timeout되고 관련 제어가 모두 다시 활성화되는지 검증했다.
- 같은 입력으로 두 번째 조작을 실행해 성공 알림과 정확히 두 번의 전송 기록을 확인했다.
- 두 복구 시나리오는 각각 10 worker 병렬 반복 10회에 통과했다.
- 단위 테스트 37개, lint, typecheck, production build, Chromium 전체 186개와 프로젝트 로그 관리 계약이 통과했다.
- CI `35221888854`(#281)의 6개 job이 6분 2초에 성공했다. Chromium 186개와 Firefox/WebKit 접근성 각 73개를 합쳐 브라우저 테스트 332개를 검증했다.

## 운영 반영

Docker Hub CD `35221889374`(#80)은 변경된 web-console만 게시하고 9분 14초에 성공했다. GitOps commit `d37cde8`은 web-console을 `sha256:af1e156ee004fde89daa4f74439107296aba1d4fa8eee6ad055d6969a0f3591d`로 갱신했다.

Canary는 20% 설정 단계(복제본 반올림으로 실제 33%), 50%, 100% 순으로 검증하고 수동 승격했다. 각 단계에서 신규 Pod Ready, 재시작 0, 정확한 digest와 공개 root·`/health` HTTPS 200을 확인했다. 최종 web-console Rollout은 새 digest로 2/2 updated·ready, Healthy이며 root의 CSP와 HSTS, health의 HSTS도 유지된다.
