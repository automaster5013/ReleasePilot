# 반복 탐색 건너뛰기 접근성 검증

## 검증 범위

VIEWER, DEVELOPER, APPROVER, OPERATOR 화면의 첫 번째 Tab 대상에 `본문으로 건너뛰기` 링크를 제공한다. 링크는 평상시 viewport 밖에 있고 키보드 포커스를 받으면 고정 위치에 표시된다. Enter로 실행하면 URL fragment가 `#main-content`로 바뀌고, 상단 브랜드·세션·로그인·로그아웃 컨트롤을 건너뛰어 본문 컨테이너에 실제 DOM 포커스가 이동한다.

브라우저 엔진별 링크 Tab 정책 차이를 피하도록 링크의 순서를 명시하고, 대상 컨테이너는 프로그래밍 방식 포커스만 받을 수 있게 했다. 네 역할에서 접근 가능한 이름, 첫 Tab 순서, 화면 노출, fragment와 최종 포커스를 검사해 WCAG 2.2 성공 기준 2.4.1 Bypass Blocks의 회귀를 차단한다.

## 검증 결과

- 단위 테스트 35개, lint, typecheck, production build 통과
- 로컬 Chromium 전체 130개 통과
- 로컬 WebKit 접근성 36개 통과
- GitHub Actions CI `35083743613`: Chromium 130개, Firefox 접근성 36개, WebKit 접근성 36개 등 총 202개 통과

## 운영 반영

Docker Hub CD `35083743777`은 Web Console 하나만 게시하고 수정 가능한 CRITICAL 취약점 차단, provenance 발급·검증, GitOps 갱신과 배포 검증을 완료했다. GitOps commit `89382b1`의 digest `sha256:23b443d3473dc2d07507cfad20672248f8885751ff19da2173d74b59c2c03c63`을 Canary 경계에서 새 Pod Ready·재시작 0으로 확인한 뒤 stable로 승격했다.

최종 확인 시 Web Console Rollout은 2/2 updated·available이고 두 Pod 모두 새 digest, Ready, 재시작 0이었다. 세 Rollout은 모두 Healthy 2/2, 네 Argo CD Application은 Synced/Healthy였다. 공개 root, `/health`, `/actuator/health/readiness`는 모두 HTTPS 200을 반환했고, root HTML의 skip 링크와 CSP 및 HSTS를 확인했다.
