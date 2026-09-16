# 키보드 포커스 가림 접근성 검증

## 검증 범위

VIEWER, DEVELOPER, APPROVER, OPERATOR 화면을 320×900 CSS px로 열고 Tab으로 이동한 각 활성 요소를 검사한다. 브라우저의 기본 포커스 스크롤이 끝난 뒤 요소 경계와 viewport의 교차 영역이 존재하는지 확인하고, 그 영역 중앙의 `elementFromPoint`가 활성 요소 또는 그 자식인지 확인한다. 따라서 포커스 대상이 화면 밖에 남거나 다른 콘텐츠에 덮이는 회귀를 탐지한다.

DOM 순서 기반 fingerprint로 이름이 같은 컨트롤도 개별 대상으로 취급한다. 브라우저 엔진과 운영체제의 Tab 정책은 서로 다르므로 방문 개수를 동일하게 강제하지 않고, 각 엔진이 방문한 대상 전체가 노출되는지를 판정한다. 이 검사는 WCAG 2.2 성공 기준 2.4.11 Focus Not Obscured (Minimum)의 자동 회귀 범위를 보강한다.

## 검증 결과

- 단위 테스트 35개, lint, typecheck, production build 통과
- 로컬 Chromium 전체 122개 통과
- 로컬 WebKit 접근성 28개 통과
- GitHub Actions CI `35079573285`: Chromium 122개, Firefox 접근성 28개, WebKit 접근성 28개 등 총 178개 통과

## 운영 반영

Docker Hub CD `35079573223`은 Web Console 하나만 게시하고 수정 가능한 CRITICAL 취약점 차단, provenance 발급·검증, GitOps 갱신과 배포 검증을 완료했다. GitOps commit `4b51c91`의 digest `sha256:0e5a9b00d6a22a7655c69ba3ee69e6796e7850658b7b269281cef24ddfdbf5ed`을 50% Canary에서 확인한 뒤 전체 stable로 승격했다.

최종 확인 시 Web Console Rollout은 2/2 updated·available, 세 Rollout은 모두 Healthy, 네 Argo CD Application은 Synced/Healthy, 모든 애플리케이션 Pod는 Ready이며 재시작 횟수는 0이었다. 공개 root, `/health`, `/actuator/health/readiness`는 모두 HTTP 200을 반환했다.

브라우저 chrome이 콘텐츠 영역을 가리는 경우, 운영체제 화면 확대 도구와 가상 키보드가 viewport를 변경하는 경우는 실제 기기 수동 검수가 필요하다.
