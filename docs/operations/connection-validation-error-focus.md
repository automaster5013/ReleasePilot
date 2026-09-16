# 외부 연결 개별 검증 오류 알림·포커스 복구 검증

## 검증 범위

Kubernetes와 Prometheus 외부 연결의 개별 `연결 검증`이 보안 토큰 조회 또는 서버 요청 단계에서 실패하면 오류를 assertive·atomic alert로 알린다. 요청 중 일시적으로 비활성화된 개별 검증 버튼이 다시 활성화되고 DOM에 연결된 다음, 오류를 발생시킨 정확한 버튼으로 키보드 포커스를 복구한다.

OPERATOR fixture에 활성 Kubernetes 연결 `Role cluster`와 활성 Prometheus 연결 `Role metrics`를 제공하고 잘못된 CSRF 응답을 사용했다. 두 개별 검증 버튼을 각각 키보드로 실행한 뒤 mutation이 전송되지 않고, 동일한 보안 토큰 오류가 alert 계약을 지키며 해당 버튼에 포커스를 돌려주는지 검증했다.

## 검증 결과

- 단위 테스트 36개, lint, typecheck, production build 통과
- 로컬 Chromium 전체 156개 통과
- 로컬 WebKit 접근성 62개 통과
- GitHub Actions CI `35123515479`: Chromium 156개, Firefox 접근성 62개, WebKit 접근성 62개 등 총 280개 통과

## 운영 반영

Docker Hub CD `35123515908`은 Web Console 하나만 게시하고 수정 가능한 CRITICAL 취약점 차단, provenance 발급·검증, GitOps 갱신과 배포 검증을 완료했다. GitOps commit `a2eb50e`의 digest `sha256:135cd49037362873076326882fb2a52d1ecf20faedac7c13bfd557b491fce0f6`를 새 Canary Pod의 Ready·재시작 0 상태에서 확인한 뒤 20%, 50%, 100% 두 승인 단계를 거쳐 stable로 승격했다.

최종 확인 시 Web Console Rollout은 2/2 updated·ready·available이고 두 Pod 모두 새 digest, Ready, 재시작 0이었다. 세 ReleasePilot Rollout은 모두 Healthy 2/2이고 sample-checkout은 1/1, 네 Argo CD Application은 Synced/Healthy였다. 공개 root, `/health`, `/actuator/health/readiness`는 모두 HTTPS 200을 반환했고 root와 readiness의 CSP 및 모든 응답의 HSTS를 확인했다.
