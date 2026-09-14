# ADR-005: Web Console은 동일 출처의 서버 세션 인증을 사용한다

- 상태: 승인됨
- 결정일: 2026-09-14
- 대상: ReleasePilot MVP

## 배경

ReleasePilot MVP의 API 소비자는 Next.js Web Console이다. 브라우저에 bearer token을 보관하거나 Web과 API를 서로 다른 출처로 분리하면 token 탈취, CORS, cookie domain과 CSRF 설정의 복잡도가 증가한다. 외부 API client와 모바일 앱은 MVP 요구사항이 아니다.

## 결정

- Web Console은 `releasepilot.kr`, Control Plane은 같은 출처의 `/api/v1`에서 제공한다.
- Spring Security 서버 세션과 Secure, HttpOnly, SameSite=Lax cookie를 사용한다.
- unsafe HTTP method에는 CSRF token을 요구한다.
- 공개 방문자는 권한이 제한되고 수명이 짧은 VIEWER demo session을 사용한다.
- 인증 이후의 도메인 권한은 User, Role, Project membership으로 평가한다.
- OIDC 제공자는 MVP 이후 교체 가능한 인증 어댑터로 둔다.

## 결과

- 브라우저 JavaScript가 장기 인증 token을 직접 보관하지 않는다.
- Web/API 간 CORS 구성이 필요하지 않다.
- 서버 세션 저장 및 CSRF 처리가 필요하다.
- 외부 CLI 또는 API client 지원 시 별도 OAuth2 client credentials 또는 token 전략이 필요하다.

## 재검토 조건

- 공개 API 또는 CLI가 MVP 핵심 범위에 포함되는 경우
- 별도 프런트엔드 origin이 필수가 되는 경우
- 조직용 SSO/OIDC가 제품 필수 요구사항이 되는 경우
