# 역할별 Web Console 접근성 검수

2026-09-16 기준 Web Console의 VIEWER, DEVELOPER, APPROVER, OPERATOR fixture 화면을
동일한 production build에서 검사한다.

## 자동 검수 범위

- `@axe-core/playwright`의 WCAG 2.0/2.1 A·AA 규칙을 네 역할 화면에 적용한다.
- VIEWER의 최근 릴리스 선택과 불러오기, DEVELOPER의 릴리스 요청, APPROVER의 승인,
  OPERATOR의 승격 버튼이 Tab 키로 도달 가능한지 확인한다.
- 키보드로 도달한 핵심 조작에는 브라우저가 계산한 visible outline이 있어야 한다.
- 검사는 기존 Playwright fixture E2E에 포함되어 `npm run test:e2e`와 CI의
  `web-console` job에서 매 변경마다 실행된다.

## 검수에서 수정한 항목

- 작은 단계 상태, 분석 근거, 감사 시각과 보조 설명의 전경색을 높여 어두운 카드 배경에서
  WCAG AA 최소 명암비를 충족시켰다.
- 승인 컨텍스트의 Pipeline 링크에 밑줄을 추가해 색상 외의 구분 수단을 제공했다.

## 검증 명령

```bash
cd apps/web-console
npm ci
npm run build
npm run test:e2e
```

자동 검사는 인지적 사용성, 실제 스크린 리더 발화 순서와 모든 브라우저·보조기기 조합을
완전히 대체하지 않는다. 운영 SSO와 실제 backend를 포함한 수동 스크린 리더 검수는 별도다.

## 운영 반영

- GitHub CI `35068309624`와 Docker Hub CD `35068309888`이 성공했다.
- 변경 감지는 Web Console 하나만 게시했고, 취약점 차단과 provenance 검증 후 GitOps commit
  `dd8d02f`가 digest `sha256:e8d8105b1f98cee8b8ff6c8072bfa2557516ac8496054d5d7a69425dd0d93274`를 반영했다.
- 두 Canary 승인 단계를 거쳐 Web Console 새 Pod 2개가 재시작 없이 Healthy가 됐다.
- 세 Rollout은 모두 2/2 Healthy, 네 Argo CD Application은 Synced/Healthy이며 공개 루트,
  Web health, Control Plane readiness는 HTTP 200이다.
