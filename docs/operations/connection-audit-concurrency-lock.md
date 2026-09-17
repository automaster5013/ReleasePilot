# 외부 연결 감사 이력 조회 동시 실행 잠금 검증

## 검증 범위

Kubernetes·Prometheus 연결의 감사 이력 조회가 React 비활성화 렌더 전에 연속 실행되더라도 두 번째 GET을 시작하지 않도록 두 연결 유형이 공유하는 동기 잠금을 적용했다. 현재 조회가 끝나기 전 같은 버튼의 중복 실행과 다른 연결 유형의 교차 실행을 모두 차단하고, 성공·실패 후 잠금을 해제해 닫기와 수동 재시도를 유지한다.

OPERATOR fixture에서 Kubernetes 연결의 감사 이력 응답을 지연시키고 같은 JavaScript task 안에서 버튼을 두 번 활성화한 뒤, 첫 요청이 대기 중인 상태에서 Prometheus 감사 이력도 실행했다. React busy 상태 렌더 여부와 관계없이 Kubernetes 감사 이력 GET만 정확히 1회 전송되고 중복·교차 조회가 시작되지 않는지 검증했다.

## 검증 결과

- 단위 테스트 36개, lint, typecheck, production build 통과
- 로컬 Chromium 전체 177개 통과
- GitHub Actions CI `35201292747`: Chromium 177개, Firefox 접근성 73개, WebKit 접근성 73개 등 총 323개 통과

## 운영 반영

Docker Hub CD `35201292912`는 Web Console 하나만 게시하고 수정 가능한 CRITICAL 취약점 차단, provenance 발급·검증, GitOps 갱신과 배포 검증을 완료했다. GitOps commit `a3bec97`의 digest `sha256:c313f3c0b9e6f1047ce290a09db10e7a0604ba224f60d26045e5ecec536e70b2`를 새 Canary Pod의 Ready·재시작 0 상태에서 확인한 뒤 20%, 50%, 100% 두 승인 단계를 거쳐 stable로 승격했다.

최종 확인 시 Web Console Rollout은 2/2 updated·ready·available이고 두 Pod 모두 새 digest, Ready, 재시작 0이었다. Argo CD Application은 Synced·Healthy였으며 공개 root와 `/health`는 HTTPS 200을 반환했고 root의 CSP와 HSTS를 확인했다.
