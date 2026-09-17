# 외부 연결 목록 새로고침 동시 실행 잠금 검증

## 검증 범위

외부 연결 목록 새로고침 버튼의 React 비활성화 렌더 이전에 같은 이벤트 루프에서 중복 실행이 발생해도 두 번째 Kubernetes·Prometheus 목록 조회를 시작하지 않도록 동기 잠금을 적용했다. 두 목록 요청을 모두 시작하기 전에 잠금을 획득하고, 둘 모두 성공하거나 하나가 실패한 뒤 잠금을 해제한다.

OPERATOR fixture에서 초기 Kubernetes·Prometheus 목록 조회를 완료한 뒤 수동 새로고침의 두 응답을 지연시켰다. 첫 수동 새로고침 직후 비활성화된 버튼에 프로그램 방식의 두 번째 클릭을 전달하고, 각 목록 API 호출이 초기 1회와 수동 1회로 유지되며 쓰기 요청 없이 완료된 뒤 버튼이 다시 활성화되는지 검증했다.

## 검증 결과

- 단위 테스트 36개, lint, typecheck, production build 통과
- 로컬 Chromium 전체 171개 통과
- 로컬 WebKit 접근성 73개 통과
- GitHub Actions CI `35171105911`: Chromium 171개, Firefox 접근성 73개, WebKit 접근성 73개 등 총 317개 통과

## 운영 반영

Docker Hub CD `35171106139`는 Web Console 하나만 게시하고 수정 가능한 CRITICAL 취약점 차단, provenance 발급·검증, GitOps 갱신과 배포 검증을 완료했다. GitOps commit `e2fc705`의 digest `sha256:e36ddc592111745cbd88f05edb282477f6c7567506df5ba1120a9cee96142dd3`를 새 Canary Pod의 Ready·재시작 0 상태에서 확인한 뒤 20%, 50%, 100% 두 승인 단계를 거쳐 stable로 승격했다.

최종 확인 시 Web Console Rollout은 2/2 updated·ready·available이고 두 Pod 모두 새 digest, Ready, 재시작 0이었다. 세 ReleasePilot Rollout은 모두 Healthy 2/2이고 네 Argo CD Application은 Synced/Healthy였다. 공개 root, `/health`, `/actuator/health/readiness`는 모두 HTTPS 200을 반환했고 root와 readiness의 CSP 및 모든 응답의 HSTS를 확인했다.
