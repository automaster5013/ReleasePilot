# 브라우저 엔진별 접근성 CI

2026-09-16부터 Web Console 기능 E2E는 Chromium에서 전부 실행하고, `@a11y`로 표시한
역할별 WCAG A·AA 및 키보드 포커스 검사는 Firefox와 WebKit에서도 추가 실행한다.

## 실행 구성

- Chromium: 전체 기능·보안·반응형·접근성 E2E 122개
- Firefox: 역할별 WCAG·키보드·고대비·텍스트 간격·동작 감소·대상 크기·포커스 가림 검사 28개
- WebKit: 역할별 WCAG·키보드·고대비·텍스트 간격·동작 감소·대상 크기·포커스 가림 검사 28개
- GitHub Actions는 `chromium firefox webkit`을 명시적으로 설치한 뒤 총 178개를 실행한다.
- Playwright 실패 리포트와 trace는 lint 입력에서 제외하고, 실패한 CI artifact로만 보관한다.

```bash
cd apps/web-console
npm run build
npx playwright test
```

로컬 Windows에서는 Chromium 전체 122개와 WebKit 접근성 28개, 총 150개가 통과했다.
이 호스트의 Playwright Firefox binary는 Windows side-by-side 런타임 구성 오류로 시작되지 않아,
Firefox 결과는 의존성을 설치한 격리된 Ubuntu GitHub runner에서 확인했다. CI 실행
`35079573285`의 Web Console job은 세 엔진 전체 178개를 통과했다.

이 검사는 브라우저 엔진별 DOM·스타일·키보드·강제 색상 동작 차이를 포착하지만 실제
스크린 리더의 발화 순서, Windows High Contrast 조합, 확대 소프트웨어와 실제 기기 조합의
수동 검수를 대체하지 않는다. 세부 검증은 [고대비·모바일 리플로 접근성 검증](high-contrast-mobile-reflow.md)을 참고한다.

## 운영 반영

- Docker Hub CD `35079573223`에서 Web Console 이미지 게시, attestation, GitOps 갱신과 배포 검증이 성공했다.
- GitOps commit `4b51c91`의 Web Console digest `sha256:0e5a9b00d6a22a7655c69ba3ee69e6796e7850658b7b269281cef24ddfdbf5ed`을 최종 stable로 승격했다.
- 세 Rollout은 2/2 Healthy, 모든 애플리케이션 Pod는 Ready이며 재시작 횟수는 0이다.
