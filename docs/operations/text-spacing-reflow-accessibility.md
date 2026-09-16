# 텍스트 간격·320px 리플로 접근성 검증

ReleasePilot Web Console은 VIEWER, DEVELOPER, APPROVER, OPERATOR 화면에서 WCAG 1.4.12의
텍스트 간격을 강제로 적용한 320px 뷰포트를 자동 검증한다. 글자·단어·줄·문단 간격이 커져도
각 역할의 핵심 작업을 사용할 수 있고 페이지 전체에 가로 스크롤이 생기지 않는지 확인한다.

## 자동 검증 범위

- 모든 요소에 줄 높이 1.5, 글자 간격 0.12em, 단어 간격 0.16em을 적용한다.
- 문단 아래 간격을 2em으로 적용한다.
- 역할별 핵심 작업 버튼이 표시되고 320px 뷰포트 안에 남는지 검사한다.
- 핵심 작업의 텍스트가 버튼 내부에서 가로·세로로 잘리지 않는지 검사한다.
- 루트 문서의 `scrollWidth`가 `clientWidth`를 넘지 않는지 검사한다.

로컬에서는 단위 테스트 35개, lint, typecheck, production build와 Chromium 110개,
WebKit 접근성 16개 등 총 126개 브라우저 시나리오가 통과했다. GitHub Actions CI
`35074202583`에서는 Chromium 110개와 Firefox·WebKit 접근성 각 16개, 총 142개를 통과했다.

## 운영 반영

- 소스 commit `113a730`에서 텍스트 간격 리플로 검사를 추가했다.
- Docker Hub CD `35074202893`은 Web Console 이미지만 게시하고 취약점 검사, provenance 생성·검증,
  GitOps 갱신과 배포 경계 검증을 모두 통과했다.
- GitOps commit `415c3b4`가 digest
  `sha256:257ea51c4821dfda0d89e20ffaa02ae1b93ddde3014710554657c60e8fee040b`를 반영했다.
- Sigstore registry bundle 전파 후 admission 검증을 통과했고, 승인된 Canary를 최종 stable로 승격했다.
- 세 Rollout은 2/2 Healthy, 모든 Pod는 Ready이고 재시작 횟수는 0이다.
- 네 Argo CD 애플리케이션은 Synced/Healthy이며 공개 루트, `/health`,
  `/actuator/health/readiness`는 HTTP 200을 반환한다.

브라우저의 강제 CSS 검사는 사용자가 지정한 모든 글꼴, 운영체제 확대 도구, 실제 스크린 리더와
브라우저 확장 조합을 완전히 재현하지 않는다. 해당 조합은 수동 출시 검수 항목으로 유지한다.
