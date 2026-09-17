# 환경 재검증·릴리스 요청 동시 실행 잠금 검증

## 검증 범위

환경 재검증과 릴리스 요청이 React busy 상태를 화면에 반영하기 전에 연속 실행되더라도 두 번째 요청을 시작하지 않도록 기존 공유 동기 잠금의 동작을 회귀 테스트로 고정했다. 재검증 실패 시에는 잠금을 해제하되 환경 검증 상태를 fail-closed로 유지해 릴리스 요청을 비활성화한다.

OPERATOR fixture에서 환경 재검증 응답을 지연시키고 같은 JavaScript task 안에서 재검증을 두 번 실행한 뒤 릴리스 요청까지 실행했다. 서버에는 환경 검증 POST가 정확히 1회만 도달하고 릴리스 생성 mutation은 발생하지 않는지 검증했다. 거부 응답 후 재검증은 다시 가능하고 릴리스 요청은 계속 차단되는지도 확인했다. 대상 테스트는 10개 병렬 worker에서 10회 반복 통과했다.

## 검증 결과

- 단위 테스트 36개, lint, typecheck, production build 통과
- 로컬 Chromium 전체 181개 및 대상 테스트 병렬 10회 반복 통과
- GitHub Actions CI `35210418090`: Chromium 181개, Firefox 접근성 73개, WebKit 접근성 73개 등 총 327개 통과

## 운영 반영

Docker Hub CD `35210418340`은 Web Console 하나만 게시하고 수정 가능한 CRITICAL 취약점 차단, provenance 발급·검증, GitOps 갱신과 배포 검증을 완료했다. GitOps commit `a5d3160`의 digest `sha256:4870abc03b4138927935aff5b1286df014bf7351940f2befe6b1719d904d273c`를 새 Canary Pod의 Ready·재시작 0 상태에서 확인한 뒤 20%, 50%, 100% 두 승인 단계를 거쳐 stable로 승격했다.

최종 확인 시 Web Console Rollout은 2/2 updated·ready·available이고 두 Pod 모두 새 digest, Ready, 재시작 0이었다. 공개 root와 `/health`는 각 Canary 단계에서 HTTPS 200을 반환했고 root의 CSP와 HSTS를 확인했다.
