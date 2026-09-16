# 세션 연결 상태 메시지 접근성 검증

## 검증 범위

VIEWER, DEVELOPER, APPROVER, OPERATOR 화면의 상단 세션·실시간 연결 상태를 `role="status"`, `aria-live="polite"`, `aria-atomic="true"`인 live region으로 제공한다. 상태 점은 장식 요소로 숨겨 화면 낭독기가 `DEMO · VIEW ONLY`, `SIGNED IN`, `RECONNECTING` 같은 상태 문자열만 완전한 단위로 알리게 했다.

네 역할에서 live region의 politeness와 atomic 계약, 역할별 초기 상태 문자열, 장식 요소 제외를 검사한다. 기존 mutation 완료 메시지 테스트는 여러 status region이 공존하는 실제 문서 구조에서 해당 메시지를 명시적으로 선택하도록 강화했다. 이 회귀 검사는 WCAG 2.2 성공 기준 4.1.3 Status Messages의 세션 연결 상태 경계를 보호한다.

## 검증 결과

- 단위 테스트 35개, lint, typecheck, production build 통과
- 로컬 Chromium 전체 138개 통과
- 로컬 WebKit 접근성 44개 통과
- GitHub Actions CI `35099117194`: Chromium 138개, Firefox 접근성 44개, WebKit 접근성 44개 등 총 226개 통과

## 운영 반영

Docker Hub CD `35099117515`은 Web Console 하나만 게시하고 수정 가능한 CRITICAL 취약점 차단, provenance 발급·검증, GitOps 갱신과 배포 검증을 완료했다. GitOps commit `9b4d8c6`의 digest `sha256:a5f74ce00a49825bb0b34b2fa7154d8b2e8a34f05cf0517178728cb709d30fbc`를 Canary 경계에서 새 Pod Ready·재시작 0으로 확인한 뒤 stable로 승격했다.

최종 확인 시 Web Console Rollout은 2/2 Ready이고 두 Pod 모두 새 digest, Ready, 재시작 0이었다. 세 Rollout은 모두 Healthy 2/2, 네 Argo CD Application은 Synced/Healthy였다. 공개 root, `/health`, `/actuator/health/readiness`는 모두 HTTPS 200을 반환했고, root HTML의 status/polite/atomic/장식 숨김 계약과 CSP 및 HSTS를 확인했다.
