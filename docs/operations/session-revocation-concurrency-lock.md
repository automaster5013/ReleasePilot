# 활성 세션 종료 동시 실행 잠금 검증

## 검증 범위

개별 활성 세션 종료와 다른 세션 일괄 종료가 React 비활성화 렌더 전에 연속 실행되더라도 두 번째 보안 mutation을 시작하지 않도록 두 동작이 공유하는 동기 잠금을 적용했다. 확인 대화상자를 취소하면 잠금을 획득하지 않으며, 요청이 성공하거나 실패한 뒤에는 잠금을 해제해 정상적인 재시도 경로를 유지한다.

OPERATOR fixture에서 개별 세션 종료 응답을 지연시키고 같은 JavaScript task 안에서 종료 버튼 이벤트를 두 번 전달한 뒤, 첫 요청이 대기 중인 상태에서 다른 세션 일괄 종료 이벤트도 전달했다. React busy 상태 렌더 여부와 관계없이 선택한 세션의 DELETE 요청만 정확히 1회 전송되고 일괄 종료 mutation은 시작되지 않는지 검증했다.

## 검증 결과

- 단위 테스트 36개, lint, typecheck, production build 통과
- 로컬 Chromium 전체 173개 통과
- 로컬 WebKit 접근성 73개 통과
- GitHub Actions CI `35190706616`: Chromium 173개, Firefox 접근성 73개, WebKit 접근성 73개 등 총 319개 통과

## 운영 반영

Docker Hub CD `35190706941`은 Web Console 하나만 게시하고 수정 가능한 CRITICAL 취약점 차단, provenance 발급·검증, GitOps 갱신과 배포 검증을 완료했다. GitOps commit `b7cea4c`의 digest `sha256:3f19ffe8e85f28418f42056235c9ca6bf055a66e8ed03b653ec8100aa4969a5e`를 새 Canary Pod의 Ready·재시작 0 상태에서 확인한 뒤 20%, 50%, 100% 두 승인 단계를 거쳐 stable로 승격했다.

최종 확인 시 Web Console Rollout은 2/2 updated·ready·available이고 두 Pod 모두 새 digest, Ready, 재시작 0이었다. 공개 root와 `/health`는 HTTPS 200을 반환했고 root의 CSP와 HSTS를 확인했다.
