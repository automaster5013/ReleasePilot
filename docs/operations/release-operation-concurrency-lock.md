# 릴리스 운영 조작 동시 실행 잠금 검증

## 검증 범위

운영자의 Promote, Pause, Resume, Abort 조작이 React의 busy 상태를 화면에 반영하기 전에 연속 실행되더라도 두 번째 mutation을 시작하지 않도록 기존 공유 동기 잠금의 동작을 회귀 테스트로 고정했다. 서로 다른 조작도 같은 잠금을 공유하며, 서버 거부 후에는 잠금과 제어 상태를 해제해 안전하게 다시 시도할 수 있다.

OPERATOR fixture에서 Promote 응답을 지연시키고 같은 JavaScript task 안에서 Promote를 두 번 실행한 뒤 Pause까지 실행했다. 서버에는 CSRF와 idempotency key로 보호된 Promote POST가 정확히 1회만 도달하고, 대기 중 Pause mutation은 발생하지 않는지 검증했다. 대상 테스트는 10개 병렬 worker에서 10회 반복해 렌더 타이밍과 무관하게 같은 결과를 확인했다.

## 검증 결과

- 단위 테스트 36개, lint, typecheck, production build 통과
- 로컬 Chromium 전체 179개 및 대상 테스트 병렬 10회 반복 통과
- GitHub Actions CI `35206463081`: Chromium 179개, Firefox 접근성 73개, WebKit 접근성 73개 등 총 325개 통과

## 운영 반영

Docker Hub CD `35206463331`은 Web Console 하나만 게시하고 수정 가능한 CRITICAL 취약점 차단, provenance 발급·검증, GitOps 갱신과 배포 검증을 완료했다. GitOps commit `a12ad3c`의 digest `sha256:2af94c6f3c7ea4583f76fb7fef8ee398ed664086dc172cebe9d82ab4c55ca029`를 새 Canary Pod의 Ready·재시작 0 상태에서 확인한 뒤 20%, 50%, 100% 두 승인 단계를 거쳐 stable로 승격했다.

최종 확인 시 Web Console Rollout은 2/2 updated·ready·available이고 두 Pod 모두 새 digest, Ready, 재시작 0이었다. 공개 root와 `/health`는 각 Canary 단계에서 HTTPS 200을 반환했고 root의 CSP와 HSTS를 확인했다.
