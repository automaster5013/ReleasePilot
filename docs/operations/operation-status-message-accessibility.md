# 운영 상태 메시지 접근성 검증

## 검증 범위

VIEWER, DEVELOPER, APPROVER, OPERATOR 화면에서 비동기 요청, 외부 연결 검증, 승인 readiness, 릴리스 조작, 세션 조작 결과를 표시하는 모든 `role="status"` 영역에 `aria-live="polite"`, `aria-atomic="true"`를 일관되게 적용한다. 화면 낭독기는 사용자의 현재 작업을 가로막지 않으면서 각 결과 메시지를 완전한 단위로 알릴 수 있다.

네 역할에서 화면에 표시된 모든 status region의 politeness와 atomic 계약을 검사한다. 이 회귀 검사는 새 상태 메시지가 추가되더라도 WCAG 2.2 성공 기준 4.1.3 Status Messages의 공통 알림 계약을 따르도록 보호한다.

## 검증 결과

- 단위 테스트 35개, lint, typecheck, production build 통과
- 로컬 Chromium 전체 142개 통과
- 로컬 WebKit 접근성 48개 통과
- GitHub Actions CI `35101356433`: Chromium 142개, Firefox 접근성 48개, WebKit 접근성 48개 등 총 238개 통과

## 운영 반영

Docker Hub CD `35101357062`는 Web Console 하나만 게시하고 수정 가능한 CRITICAL 취약점 차단, provenance 발급·검증, GitOps 갱신과 배포 검증을 완료했다. GitOps commit `f80a956`의 digest `sha256:cbe1a36450bacc4ab2ca9e206aeeac6469c3a8dae82cc8b62b0a58a62e818606`을 Canary 경계에서 새 Pod Ready·재시작 0으로 확인한 뒤 두 승인 단계를 거쳐 stable로 승격했다.

최종 확인 시 Web Console Rollout은 2/2 Ready이고 두 Pod 모두 새 digest, Ready, 재시작 0이었다. 세 Rollout은 모두 Healthy 2/2, 네 Argo CD Application은 Synced/Healthy였다. 공개 root, `/health`, `/actuator/health/readiness`는 모두 HTTPS 200을 반환했고, root HTML의 status/polite/atomic 계약과 CSP 및 HSTS를 확인했다.
