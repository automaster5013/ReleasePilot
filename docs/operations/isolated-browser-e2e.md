# 격리된 웹 브라우저 E2E

`apps/web-console/e2e/viewer.spec.ts`는 실제 production Next.js 빌드를 Chromium에서 실행하고 `/control-api`만 테스트 fixture로 대체한다. CSP를 우회하지 않고 DB, EKS, OIDC, 실제 릴리스 mutation에 접근하지 않는다. 각 테스트는 별도 브라우저 context와 인증 상태를 사용한다.

Docker와 동일하게 standalone server에 static/public asset을 복사해 실행한다. 이 복사는 Git에 포함되지 않는 `.next/standalone` 빌드 산출물에만 적용된다.

## 실행

`apps/web-console`에서 다음 순서로 실행한다.

```sh
npm ci
npx playwright install chromium
npm run build
npm run test:e2e
```

Linux에서 브라우저 OS 의존성이 없으면 `npx playwright install --with-deps chromium`을 사용한다. runner는 `127.0.0.1:3100`에 production 서버를 시작하고 종료 시 정리한다. 기존 서버를 재사용하지 않으므로 해당 포트를 비워 둔다. 외부 base URL을 받는 옵션은 제공하지 않는다.

## 검증 범위

1. production CSP nonce가 실제 script와 일치하고 매 요청 변경되는지, inline/eval 허용 없이 로그인 버튼이 작동하는지
2. 데모 로그인 후 새로고침에서 상단 표시와 읽기 전용 상태가 복원되는지
3. fixture 릴리스 A/B 선택에 따라 context, 분석 query hash와 감사 chain/event가 교체되는지
4. 다른 릴리스 조회가 403일 때 이전 대상 상세를 제거하고 조작을 차단하는지

예상하지 못한 API 요청이나 demo 진입 이외의 mutation, 외부 origin 요청은 실패로 기록하며 실제 API로 넘기지 않는다. `/control-api` 외의 요청은 production 서버의 HTML/정적 asset을 사용한다. mock된 401/403은 서버 RBAC 검증이 아니라 해당 응답에 대한 UI 검증이다.

## CI와 산출물

웹 CI에서 production build 다음 Chromium 설치와 E2E를 수행한다. 실패 시 `playwright-report/`와 `test-results/`의 trace/screenshot을 artifact로 7일 보관한다. 로컬 산출물은 Git에서 제외한다.

이 테스트가 통과해도 실제 운영 릴리스 상세·DB·SSE 지속 연결·조직 SSO·서버 권한·AWS ingress E2E 통과를 의미하지 않는다. 해당 검증은 승인된 격리 환경과 계정/실제 테스트 릴리스가 필요하다.

## 최초 검증 결과 (2026-09-15)

로컬 Chromium에서 4개 시나리오를 3회씩 반복해 12회 모두 통과했다. 웹 단위 테스트 34개, lint, typecheck와 production build도 통과했다. 운영 이미지나 운영 릴리스 데이터는 변경하지 않았다.
