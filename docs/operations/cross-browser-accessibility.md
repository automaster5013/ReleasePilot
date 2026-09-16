# 브라우저 엔진별 접근성 CI

2026-09-16부터 Web Console 기능 E2E는 Chromium에서 전부 실행하고, `@a11y`로 표시한
역할별 WCAG A·AA 및 키보드 포커스 검사는 Firefox와 WebKit에서도 추가 실행한다.

## 실행 구성

- Chromium: 전체 기능·보안·반응형·접근성 E2E 102개
- Firefox: 역할별 접근성·키보드 검사 8개
- WebKit: 역할별 접근성·키보드 검사 8개
- GitHub Actions는 `chromium firefox webkit`을 명시적으로 설치한 뒤 총 118개를 실행한다.
- Playwright 실패 리포트와 trace는 lint 입력에서 제외하고, 실패한 CI artifact로만 보관한다.

```bash
cd apps/web-console
npm run build
npx playwright test
```

로컬 Windows에서는 Chromium 전체 102개와 WebKit 접근성 8개, 총 110개가 통과했다.
이 호스트의 Playwright Firefox binary는 Windows side-by-side 런타임 구성 오류로 시작되지 않아,
Firefox 결과는 의존성을 설치한 격리된 Ubuntu GitHub runner에서 확인했다. CI 실행
`35069804438`의 Web Console job은 세 엔진 전체 118개를 통과했다.

이 검사는 브라우저 엔진별 DOM·스타일·키보드 동작 차이를 포착하지만 실제 스크린 리더의
발화 순서, 고대비 모드, 확대 소프트웨어와 실제 기기 조합의 수동 검수를 대체하지 않는다.

## 운영 반영

- Docker Hub CD `35069804815`에서 세 이미지의 취약점 차단과 provenance 검증이 성공했다.
- GitOps commit `3281175`의 새 digest로 세 Rollout을 두 단계 승격했다.
- 세 Rollout은 2/2 Healthy, 모든 애플리케이션 Pod는 Ready이며 재시작 횟수는 0이다.
