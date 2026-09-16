# 페이지 제목·랜드마크 계층 접근성 검증

## 검증 범위

VIEWER, DEVELOPER, APPROVER, OPERATOR 화면에서 문서 제목을 `ReleasePilot — Safe delivery control plane`으로 유지하고, 페이지의 유일한 `main` 랜드마크를 `Progressive delivery control room` H1으로 명시적으로 이름 붙였다. 상단 브랜드·세션·인증 제어를 포함하는 `navigation` 랜드마크에는 `주요 탐색 및 계정 제어`라는 고유한 이름을 제공한다.

네 역할에서 문서 제목, 이름 있는 `main`과 `navigation`, 단 하나의 H1을 브라우저 접근성 트리로 확인한다. 이 회귀 검사는 WCAG 2.2 성공 기준 1.3.1 Info and Relationships와 2.4.2 Page Titled의 페이지 구조 경계를 보호한다.

## 검증 결과

- 단위 테스트 35개, lint, typecheck, production build 통과
- 로컬 Chromium 전체 134개 통과
- 로컬 WebKit 접근성 40개 통과
- GitHub Actions CI `35088029774`: Chromium 134개, Firefox 접근성 40개, WebKit 접근성 40개 등 총 214개 통과

## 운영 반영

Docker Hub CD `35088030043`은 Web Console 하나만 게시하고 수정 가능한 CRITICAL 취약점 차단, provenance 발급·검증, GitOps 갱신과 배포 검증을 완료했다. GitOps commit `17f733f`의 digest `sha256:c0883e475cc1f8ffd47c6e66c6fefa1e9b0375ec7c73a977808063d561026d0f`을 Canary 경계에서 새 Pod Ready·재시작 0으로 확인한 뒤 stable로 승격했다.

최종 확인 시 Web Console Rollout은 2/2 Ready이고 두 Pod 모두 새 digest, Ready, 재시작 0이었다. 세 Rollout은 모두 Healthy 2/2, 네 Argo CD Application은 Synced/Healthy였다. 공개 root, `/health`, `/actuator/health/readiness`는 모두 HTTPS 200을 반환했고, root HTML의 문서 제목·`main`/`navigation` 이름과 CSP 및 HSTS를 확인했다.
