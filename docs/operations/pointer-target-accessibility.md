# 포인터 대상 크기 접근성 검증

ReleasePilot Web Console은 320px 화면에서 VIEWER, DEVELOPER, APPROVER, OPERATOR에게 표시되는
버튼, 입력, 선택 컨트롤과 독립 링크가 WCAG 2.5.8의 최소 24×24 CSS 픽셀을 충족하는지 검증한다.
본문 문장 안의 인라인 링크는 이 성공 기준의 크기 예외에 따라 검사 대상에서 제외한다.

실제 production fixture 렌더 트리에서 숨겨진 요소를 제외하고 각 대상의 bounding box를 측정한다.
로컬에서 단위 테스트 35개, lint, typecheck, production build와 Chromium 118개,
WebKit 접근성 24개 등 총 142개 브라우저 시나리오가 통과했다. GitHub Actions CI
`35077392346`에서는 Chromium 118개와 Firefox·WebKit 접근성 각 24개, 총 166개를 통과했다.

## 운영 반영

- 소스 commit `1d30699`에 네 역할의 대상 크기 검사를 반영했다.
- Docker Hub CD `35077392625`은 Web Console 이미지의 취약점 검사, provenance 생성·검증,
  GitOps 갱신과 배포 경계 검증을 통과했다.
- GitOps commit `17f7511`이 digest
  `sha256:a3aa789213d8e57cee741ed4ccaa4ef0860880cf4567c411c6e8d0ad77352147`을 반영했다.
- Sigstore admission과 Canary 검증 후 새 digest를 최종 stable로 승격했다.
- 세 Rollout은 2/2 Healthy, 모든 Pod는 Ready이고 재시작 횟수는 0이다.
- 네 Argo CD 애플리케이션은 Synced/Healthy이며 공개 루트, `/health`,
  `/actuator/health/readiness`는 HTTP 200을 반환한다.

자동 검사는 CSS 픽셀 기준의 대상 자체 크기를 검증한다. 실제 기기의 물리적 크기, 손 떨림,
보조 포인터 장치와 인라인 링크 주변 간격의 사용성 평가는 수동 검수 범위다.
