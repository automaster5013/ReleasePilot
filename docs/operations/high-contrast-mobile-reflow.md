# 고대비·모바일 리플로 접근성 검증

ReleasePilot Web Console은 VIEWER, DEVELOPER, APPROVER, OPERATOR 화면을 320px 너비와
브라우저 강제 색상 모드에서 자동 검증한다. 각 역할의 핵심 작업 버튼이 화면 안에 표시되고,
문서에 가로 스크롤이 생기지 않으며, Tab 키로 해당 버튼에 도달했을 때 포커스가 보이는지 확인한다.

## 구현 범위

- 페이지와 카드가 좁은 그리드에서 콘텐츠 최소 너비 때문에 확장되지 않도록 `min-width: 0`을 적용했다.
- 증거 표는 카드 안에서만 가로로 스크롤되며 페이지 전체 너비에는 영향을 주지 않는다.
- WebKit에서도 내부 표 너비가 루트 문서로 전파되지 않도록 페이지와 증거 카드의 overflow 경계를 명시했다.
- Chromium 전체 기능 E2E에 네 검사를 추가하고 Firefox·WebKit의 `@a11y` 묶음에도 같은 검사를 추가했다.

```bash
cd apps/web-console
npm test
npm run lint
npm run typecheck
npm run build
npx playwright test
```

로컬에서는 단위 테스트 35개, lint, typecheck, build와 Chromium 106개·WebKit 접근성
12개 등 총 118개 브라우저 시나리오가 통과했다. GitHub Actions CI `35071980684`에서는
Chromium 106개와 Firefox·WebKit 접근성 각 12개, 총 130개를 통과했다.

## 운영 반영

- Docker Hub CD `35071980747`에서 Web Console 이미지 게시, attestation, GitOps 갱신과 배포 검증이 성공했다.
- GitOps commit `2c72af6`이 Web Console digest
  `sha256:f13a8e8d196dbca758efc2a451bfaaa6fa8c5ae6beae19cd132b11f936e13910`을 반영했고,
  이 revision은 다음 접근성 배포가 이어지면서 최종 stable 승격 전에 대체되었다.
- 후속 텍스트 간격 리플로 배포에서 세 Rollout 2/2 Healthy, 모든 애플리케이션 Pod Ready와
  재시작 횟수 0을 최종 확인했다.
- 네 Argo CD 애플리케이션은 Synced/Healthy이며 공개 루트, `/health`,
  `/actuator/health/readiness`는 HTTP 200을 반환한다.

강제 색상 에뮬레이션은 실제 Windows High Contrast 조합, 화면 확대 도구와 스크린 리더의
발화 및 탐색 순서를 완전히 재현하지 않는다. 해당 조합은 수동 출시 검수 항목으로 유지한다.
