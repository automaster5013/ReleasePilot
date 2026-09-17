# 릴리스 요청 동시 실행 잠금 검증

## 검증 범위

개발자가 릴리스 요청을 시작한 직후 React busy 상태가 화면에 반영되기 전에 같은 제어가 연속 활성화되더라도 두 번째 요청을 시작하지 않도록 기존 동기 잠금의 동작을 회귀 테스트로 고정했다. 서버 거부 후에는 잠금과 제어 상태를 해제해 안전하게 다시 시도할 수 있다.

DEVELOPER fixture에서 릴리스 생성 응답을 지연시키고 같은 JavaScript task 안에서 릴리스 요청을 두 번 실행했다. 서버에는 전체 추적 정보와 정책 선택, CSRF 및 idempotency key로 보호된 릴리스 생성 POST가 정확히 1회만 도달하는지 검증했다. 대상 테스트는 10개 병렬 worker에서 10회 반복 통과했다.

## 검증 결과

- 단위 테스트 36개, lint, typecheck, production build 통과
- 로컬 Chromium 전체 182개 및 대상 테스트 병렬 10회 반복 통과
- GitHub Actions CI `35212328141`: Chromium 182개, Firefox 접근성 73개, WebKit 접근성 73개 등 총 328개 통과

## 운영 반영

Docker Hub CD `35212328451`은 Web Console 하나만 게시하고 수정 가능한 CRITICAL 취약점 차단, provenance 발급·검증, GitOps 갱신과 배포 검증을 완료했다. GitOps commit `0f41f0c`의 digest `sha256:11d2c224587966a26b4de29877cac1092a1061edc8a6aac5db88a48182b070f4`를 새 Canary Pod의 Ready·재시작 0 상태에서 확인하고 구성된 20%, 50%, 100% 단계를 거쳐 stable로 승격했다.

최종 확인 시 Web Console Rollout은 2/2 updated·ready·available이고 두 Pod 모두 새 digest, Ready, 재시작 0이었다. 공개 root와 `/health`는 HTTPS 200을 반환했고 root의 CSP와 HSTS를 확인했다.
