# 샘플 표시·모바일 상단 UI 개선

2026-09-15

실제 릴리스를 선택하지 않아도 샘플 카드가 PRODUCTION RELEASE로 표시되던 문제를 수정했다. 미선택 화면에는 샘플 상태·판정 근거가 실제 운영 결과가 아니라는 안내와 `SAMPLE RELEASE · 예시`를 표시한다. 실제 릴리스 상세를 불러오면 안내를 제거하고 `SELECTED RELEASE`로 전환한다. 실제 릴리스의 환경을 임의로 production으로 분류하지 않는다.

상단 브랜드와 세션·로그인 컨트롤을 별도로 배치하고 모바일에서는 두 행으로 나눈다. 데모 버튼 스타일과 키보드 focus 표시를 추가했다. 조회 select는 모바일에서 남은 폭을 사용하고 버튼은 최소 44px 높이와 한 줄 텍스트를 유지한다. 기존 데모/SSO 동작과 VIEWER 권한은 유지한다.

검증: 웹 단위 34개, lint, typecheck, 새 production build 및 fixture Chromium E2E 9개 통과. 추가 5개는 샘플→선택 릴리스 표시 전환과 320/390/800/1024px 상단 겹침·페이지 가로 넘침·조회 버튼 높이 검증이다. 최초 반응형 테스트는 브랜드 exact-text 선택자 오류로 timeout했으며 선택자 수정 후 전체 9개를 다시 실행해 통과했다. 320/390/1024px 전체 페이지 PNG를 직접 확인했고 800px은 자동 검증·캡처했다. 표의 가로 스크롤은 기존 설계를 유지한다.

```powershell
cd C:\ReleasePilot\apps\web-console
npm test
npm run lint
npm run typecheck
npm run build
npm run test:e2e
```

fixture API와 production standalone을 사용하는 로컬 UI 검증이다. 실제 운영 릴리스 전체 E2E, 역할별 DEVELOPER/APPROVER/OPERATOR 화면 인수, SSO 공급자, 전체 접근성 감사·교차 브라우저는 미검증이다. 새 운영 배포를 하지 않았으므로 공개 v0.55.0에는 이번 수정이 아직 반영되지 않았다. 운영 데이터·인증·인프라 변경과 실제 릴리스 mutation은 없다.
