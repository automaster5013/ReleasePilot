# 키보드 포커스 외관 접근성 검증

## 검증 범위

VIEWER, DEVELOPER, APPROVER, OPERATOR 화면을 320×900 CSS px로 열고 브라우저가 Tab으로 방문하는 모든 활성 요소의 포커스 표시를 검사한다. 공통 `:focus-visible` 표시는 2px solid 외곽선과 3px offset을 사용하며, 자동 검사는 표시 존재 여부, 최소 2px 두께, 페이지 배경 대비 3:1 이상을 확인한다. 기본 컨트롤뿐 아니라 `summary`와 스크롤 가능한 표처럼 브라우저가 암시적으로 Tab 대상에 포함하는 요소도 동일한 표시를 받는다.

이 검사는 WCAG 2.2 성공 기준 2.4.13 Focus Appearance의 최소 면적과 인접 색상 대비를 지속적으로 확인한다. 복잡한 배경 이미지, 운영체제 고대비 테마가 색을 대체하는 경우, 브라우저 chrome과 화면 확대 도구 조합은 실제 기기 수동 검수 범위로 남긴다.

## 검증 결과

- 단위 테스트 35개, lint, typecheck, production build 통과
- 로컬 Chromium 전체 126개 통과
- 로컬 WebKit 접근성 32개 통과
- GitHub Actions CI `35081357389`: Chromium 126개, Firefox 접근성 32개, WebKit 접근성 32개 등 총 190개 통과

## 운영 반영

Docker Hub CD `35081357865`은 Web Console 하나만 게시하고 수정 가능한 CRITICAL 취약점 차단, provenance 발급·검증, GitOps 갱신과 배포 검증을 완료했다. GitOps commit `f22fbe8`의 digest `sha256:41dbdcaed935a9dece6bccb4320f570fed6cabaaf9290bd9d8ccd01041c375ea`을 50% Canary에서 확인한 뒤 전체 stable로 승격했다.

최종 확인 시 Web Console Rollout은 2/2 updated·available이고 두 Pod 모두 새 digest, Ready, 재시작 0이었다. 세 Rollout은 모두 Healthy 2/2, 네 Argo CD Application은 Synced/Healthy였다. 공개 root, `/health`, `/actuator/health/readiness`는 모두 HTTPS 200을 반환했고 root의 CSP와 세 endpoint의 HSTS를 확인했다.
