# 외부 연결 목록 새로고침 오류 알림·포커스 복구 검증

## 검증 범위

Kubernetes 또는 Prometheus 외부 연결 목록 새로고침이 실패하면 오류를 assertive·atomic alert로 알린다. 요청 중 새로고침 버튼을 비활성화하고 진행 상태를 표시하며, 버튼이 다시 활성화되고 DOM에 연결된 다음 동일 버튼으로 키보드 포커스를 복구한다.

OPERATOR fixture의 최초 목록 조회는 성공시키고 사용자가 실행한 후속 Kubernetes·Prometheus 목록 조회는 503을 반환하도록 구성했다. 새로고침 버튼을 키보드로 실행한 뒤 mutation이 전송되지 않고, 조회 오류가 alert 계약을 지키며 새로고침 버튼에 포커스를 돌려주는지 검증했다.

## 검증 결과

- 단위 테스트 36개, lint, typecheck, production build 통과
- 로컬 Chromium 전체 161개 통과
- 로컬 WebKit 접근성 67개 통과
- GitHub Actions CI `35151300957`: Chromium 161개, Firefox 접근성 67개, WebKit 접근성 67개 등 총 295개 통과

## 운영 반영

Docker Hub CD `35151301431`은 Web Console 하나만 게시하고 수정 가능한 CRITICAL 취약점 차단, provenance 발급·검증, GitOps 갱신과 배포 검증을 완료했다. GitOps commit `7bd9cb3`의 digest `sha256:7bb216a73daa3465f08ccd49e520b40451cb3d7f0a672a6e49dda45b46599643`를 새 Canary Pod의 Ready·재시작 0 상태에서 확인한 뒤 20%, 50%, 100% 두 승인 단계를 거쳐 stable로 승격했다.

최종 확인 시 Web Console Rollout은 2/2 updated·ready·available이고 두 Pod 모두 새 digest, Ready, 재시작 0이었다. 세 ReleasePilot Rollout은 모두 Healthy 2/2이고 sample-checkout은 1/1, 네 Argo CD Application은 Synced/Healthy였다. 공개 root, `/health`, `/actuator/health/readiness`는 모두 HTTPS 200을 반환했고 root와 readiness의 CSP 및 모든 응답의 HSTS를 확인했다.
