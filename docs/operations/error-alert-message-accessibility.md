# 오류 경고 메시지 접근성 검증

## 검증 범위

VIEWER, DEVELOPER, APPROVER, OPERATOR 화면에서 로그인·보안 토큰·릴리스 요청·카탈로그·감사 체인 오류를 표시하는 모든 사용자 가시 `role="alert"` 영역에 `aria-live="assertive"`, `aria-atomic="true"`를 일관되게 적용한다. 화면 낭독기는 즉시 조치가 필요한 오류를 우선 알리고 각 오류 메시지를 완전한 단위로 전달할 수 있다.

네 역할에서 실제 잘못된 JSON 또는 잘못된 CSRF 응답을 발생시켜 사용자 가시 alert가 생성되는지, assertive와 atomic 계약을 모두 지키는지 검사한다. mutation 역할에서는 오류 발생 전에 서버 변경이 전송되지 않은 것도 함께 확인한다. 이 회귀 검사는 WCAG 2.2 성공 기준 4.1.3 Status Messages의 긴급 오류 알림 경계를 보호한다.

## 검증 결과

- 단위 테스트 35개, lint, typecheck, production build 통과
- 로컬 Chromium 전체 146개 통과
- 로컬 WebKit 접근성 52개 통과
- GitHub Actions CI `35104239470`: Chromium 146개, Firefox 접근성 52개, WebKit 접근성 52개 등 총 250개 통과

## 운영 반영

Docker Hub CD `35104239583`은 Web Console 하나만 게시하고 수정 가능한 CRITICAL 취약점 차단, provenance 발급·검증, GitOps 갱신과 배포 검증을 완료했다. GitOps commit `6a57f39`의 digest `sha256:f66895e09e149eb1c3b477a21232f786196838afa92c8cec85d5cc2019c6fed5`를 Canary 경계에서 새 Pod Ready·재시작 0으로 확인한 뒤 두 승인 단계를 거쳐 stable로 승격했다.

최종 확인 시 Web Console Rollout은 2/2 Ready이고 두 Pod 모두 새 digest, Ready, 재시작 0이었다. 세 Rollout은 모두 Healthy 2/2, 네 Argo CD Application은 Synced/Healthy였다. 공개 root, `/health`, `/actuator/health/readiness`는 모두 HTTPS 200을 반환했고 CSP 및 HSTS를 확인했다. 오류 alert는 조건부 렌더링 영역이므로 배포된 동일 이미지에 대한 다중 브라우저 오류 fixture로 assertive/atomic 계약을 검증했다.
