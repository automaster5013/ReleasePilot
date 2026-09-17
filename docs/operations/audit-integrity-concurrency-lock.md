# 감사 체인 검증 동시 실행 잠금 검증

## 검증 범위

감사 체인 검증 버튼의 React 비활성화 렌더 이전에 같은 이벤트 루프에서 중복 실행이 발생해도 두 번째 검증 요청을 시작하지 않도록 동기 잠금을 적용했다. 수동 검증 시작 전에 잠금을 획득하고 성공 또는 실패 후 해제한다.

OPERATOR fixture에서 초기 자동 검증을 완료한 뒤 수동 검증 응답을 지연시켰다. 첫 수동 검증 직후 비활성화된 버튼에 프로그램 방식의 두 번째 클릭을 전달하고, 검증 API 호출이 초기 1회와 수동 1회로 유지되며 쓰기 요청 없이 완료된 뒤 버튼이 다시 활성화되는지 검증했다.

## 검증 결과

- 단위 테스트 36개, lint, typecheck, production build 통과
- 로컬 Chromium 전체 170개 통과
- 로컬 WebKit 접근성 73개 통과
- GitHub Actions CI `35168323247`: Chromium 170개, Firefox 접근성 73개, WebKit 접근성 73개 등 총 316개 통과

## 운영 반영

Docker Hub CD `35168323445`는 Web Console 하나만 게시하고 수정 가능한 CRITICAL 취약점 차단, provenance 발급·검증, GitOps 갱신과 배포 검증을 완료했다. GitOps commit `4c76817`의 digest `sha256:03f7f03211f650734b97a7ab026360be79af6e5433b3afb8e3c9dc0e7b397904`를 새 Canary Pod의 Ready·재시작 0 상태에서 확인한 뒤 20%, 50%, 100% 두 승인 단계를 거쳐 stable로 승격했다.

최종 확인 시 Web Console Rollout은 2/2 updated·ready·available이고 두 Pod 모두 새 digest, Ready, 재시작 0이었다. 세 ReleasePilot Rollout은 모두 Healthy 2/2이고 네 Argo CD Application은 Synced/Healthy였다. 공개 root, `/health`, `/actuator/health/readiness`는 모두 HTTPS 200을 반환했고 root와 readiness의 CSP 및 모든 응답의 HSTS를 확인했다.
