# 릴리스 상세 조회 오류 알림·포커스 복구 검증

## 검증 범위

선택한 릴리스의 상세 조회가 실패하면 오류를 assertive·atomic alert로 알린다. 조회 중 릴리스 선택·조회·새로고침 제어를 비활성화해 중복 요청을 막고, 제어가 다시 활성화되고 DOM에 연결된 다음 동일한 조회 버튼으로 키보드 포커스를 복구한다.

OPERATOR fixture에서 릴리스 상세 API가 503을 반환하도록 구성했다. `불러오기` 버튼을 키보드로 실행한 뒤 쓰기 요청이 전송되지 않으며, 오류가 alert 계약을 지키고 같은 조회 버튼에 포커스를 돌려주는지 검증했다.

## 검증 결과

- 단위 테스트 36개, lint, typecheck, production build 통과
- 로컬 Chromium 전체 167개 통과
- 로컬 WebKit 접근성 73개 통과
- GitHub Actions CI `35163266001`: Chromium 167개, Firefox 접근성 73개, WebKit 접근성 73개 등 총 313개 통과

## 운영 반영

Docker Hub CD `35163266109`는 Web Console 하나만 게시하고 수정 가능한 CRITICAL 취약점 차단, provenance 발급·검증, GitOps 갱신과 배포 검증을 완료했다. GitOps commit `e068087`의 digest `sha256:2e22ee1cf7eb8c82915b6ecb4f4ae11bd49db15251f75e363c79e06b0da422e8`를 새 Canary Pod의 Ready·재시작 0 상태에서 확인한 뒤 20%, 50%, 100% 두 승인 단계를 거쳐 stable로 승격했다.

최종 확인 시 Web Console Rollout은 2/2 updated·ready·available이고 두 Pod 모두 새 digest, Ready, 재시작 0이었다. 세 ReleasePilot Rollout은 모두 Healthy 2/2이고 sample-checkout은 1/1, 네 Argo CD Application은 Synced/Healthy였다. 공개 root, `/health`, `/actuator/health/readiness`는 모두 HTTPS 200을 반환했고 root와 readiness의 CSP 및 모든 응답의 HSTS를 확인했다.
