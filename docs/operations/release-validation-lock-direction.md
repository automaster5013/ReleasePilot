# 릴리스 요청·환경 재검증 역방향 동시 실행 잠금 검증

## 검증 범위

릴리스 요청을 먼저 시작한 직후 React busy 상태가 화면에 반영되기 전에 릴리스 요청을 다시 제출하거나 환경 재검증을 실행해도, 두 기능이 공유하는 기존 동기 잠금이 후속 mutation을 차단하는지 역방향으로 검증했다. 서버 거부 후에는 잠금과 두 제어 상태가 모두 해제되어 안전하게 다시 시도할 수 있다.

OPERATOR fixture에서 릴리스 생성 응답을 지연시키고 같은 JavaScript task 안에서 릴리스 요청을 두 번 제출한 뒤 환경 재검증을 실행했다. 서버에는 CSRF 및 idempotency key로 보호된 `/releases` 요청이 정확히 1회만 도달하고 환경 재검증 mutation은 발생하지 않는지 확인했다. 이 과정에서 제품 권한 모델과 달리 fixture가 DEVELOPER만 릴리스 요청을 허용하던 차이를 발견해 APPROVER와 OPERATOR도 실제 제품 계약과 동일하게 허용하도록 바로잡았다. 대상 테스트는 10개 병렬 worker에서 10회 반복 통과했다.

## 검증 결과

- 단위 테스트 36개, lint, typecheck, production build 통과
- 로컬 Chromium 전체 183개 및 대상 테스트 병렬 10회 반복 통과
- GitHub Actions CI `35214424099`: Chromium 183개, Firefox 접근성 73개, WebKit 접근성 73개 등 총 329개 통과

## 운영 반영

Docker Hub CD `35214424169`은 Web Console 하나만 게시하고 수정 가능한 CRITICAL 취약점 차단, provenance 발급·검증, GitOps 갱신과 배포 검증을 완료했다. GitOps commit `edf1f9d`의 digest `sha256:7990c59436336fb900af1c7b768c5c6c27970f468d577b69333927bd8a1bbfb2`를 새 Canary Pod의 Ready·재시작 0 상태에서 확인하고 구성된 20%, 50%, 100% 단계를 거쳐 stable로 승격했다.

최종 확인 시 Web Console Rollout은 2/2 updated·ready·available이고 두 Pod 모두 새 digest, Ready, 재시작 0이었다. 공개 root와 `/health`는 HTTPS 200을 반환했고 root의 CSP와 HSTS를 확인했다.
