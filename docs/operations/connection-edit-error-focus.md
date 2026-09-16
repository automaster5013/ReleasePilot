# 외부 연결 편집 오류 알림·포커스 복구 검증

## 검증 범위

Kubernetes와 Prometheus 외부 연결 편집이 보안 토큰 조회 또는 서버 요청 단계에서 실패하면 오류를 assertive·atomic alert로 알린다. 요청 중 일시적으로 비활성화된 편집 버튼이 다시 활성화되고 DOM에 연결된 다음, 오류를 발생시킨 정확한 버튼으로 키보드 포커스를 복구한다.

OPERATOR fixture에 활성 Kubernetes와 Prometheus 연결을 제공하고 잘못된 CSRF 응답을 사용했다. 기존의 유효한 값을 유지한 채 두 편집 버튼을 각각 키보드로 실행한 뒤 mutation이 전송되지 않고, 동일한 보안 토큰 오류가 alert 계약을 지키며 해당 버튼에 포커스를 돌려주는지 검증했다.

## 검증 결과

- 단위 테스트 36개, lint, typecheck, production build 통과
- 로컬 Chromium 전체 159개 통과
- 로컬 WebKit 접근성 65개 통과
- GitHub Actions CI `35145596137`: Chromium 159개, Firefox 접근성 65개, WebKit 접근성 65개 등 총 289개 통과

## 운영 반영

Docker Hub CD `35145596599`는 Web Console 하나만 게시하고 수정 가능한 CRITICAL 취약점 차단, provenance 발급·검증, GitOps 갱신과 배포 검증을 완료했다. GitOps commit `412c8a8`의 digest `sha256:0a26f662b6ece9b4d2fdcdbde872b41415ef06245e7244f5fd8fe03968b16023`를 새 Canary Pod의 Ready·재시작 0 상태에서 확인한 뒤 20%, 50%, 100% 두 승인 단계를 거쳐 stable로 승격했다.

최종 확인 시 Web Console Rollout은 2/2 updated·ready·available이고 두 Pod 모두 새 digest, Ready, 재시작 0이었다. 세 ReleasePilot Rollout은 모두 Healthy 2/2이고 sample-checkout은 1/1, 네 Argo CD Application은 Synced/Healthy였다. 공개 root, `/health`, `/actuator/health/readiness`는 모두 HTTPS 200을 반환했고 root와 readiness의 CSP 및 모든 응답의 HSTS를 확인했다.
