# 외부 연결 감사 이력 오류 알림·포커스 복구 검증

## 검증 범위

Kubernetes와 Prometheus 외부 연결 감사 이력 조회가 실패하면 오류를 assertive·atomic alert로 알린다. 조회 중 일시적으로 비활성화된 감사 이력 버튼이 다시 활성화되고 DOM에 연결된 다음, 오류를 발생시킨 정확한 버튼으로 키보드 포커스를 복구한다.

OPERATOR fixture에 활성 Kubernetes와 Prometheus 연결을 제공하고 두 연결 유형의 감사 API가 503을 반환하도록 구성했다. 두 감사 이력 버튼을 각각 키보드로 실행한 뒤 mutation이 전송되지 않고, 조회 오류가 alert 계약을 지키며 해당 버튼에 포커스를 돌려주는지 검증했다.

## 검증 결과

- 단위 테스트 36개, lint, typecheck, production build 통과
- 로컬 Chromium 전체 160개 통과
- 로컬 WebKit 접근성 66개 통과
- GitHub Actions CI `35149558423`: Chromium 160개, Firefox 접근성 66개, WebKit 접근성 66개 등 총 292개 통과

## 운영 반영

Docker Hub CD `35149558882`는 Web Console 하나만 게시하고 수정 가능한 CRITICAL 취약점 차단, provenance 발급·검증, GitOps 갱신과 배포 검증을 완료했다. GitOps commit `055b300`의 digest `sha256:edb7c521441c93aad3a8f05918a9200ac66c4976ab37bf16cae35d2007a4ebff`를 새 Canary Pod의 Ready·재시작 0 상태에서 확인한 뒤 20%, 50%, 100% 두 승인 단계를 거쳐 stable로 승격했다.

최종 확인 시 Web Console Rollout은 2/2 updated·ready·available이고 두 Pod 모두 새 digest, Ready, 재시작 0이었다. 세 ReleasePilot Rollout은 모두 Healthy 2/2이고 sample-checkout은 1/1, 네 Argo CD Application은 Synced/Healthy였다. 공개 root, `/health`, `/actuator/health/readiness`는 모두 HTTPS 200을 반환했고 root와 readiness의 CSP 및 모든 응답의 HSTS를 확인했다.
