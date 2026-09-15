# 서버 CSRF 검증

2026-09-16: `DemoSessionApiTests`에 서버 CSRF 보호 회귀 테스트 2개를 추가했다.

- 토큰 없는 데모 로그인은 403이며 인증된 세션을 만들지 않는다.
- 실제 `/api/v1/session/csrf` 응답에서 헤더 이름·토큰을 읽는다. 위조 토큰 및 다른 세션의 토큰 재사용은 403이며 원래 세션은 미인증 상태를 유지한다.
- 같은 세션에서 발급받은 정상 토큰을 응답에 지정된 헤더로 전송하면 데모 로그인과 VIEWER 세션 조회가 성공한다.

실제 Spring Security 필터 및 서버 컨트롤러를 로컬 H2 테스트 환경에서 실행한다. 정상 경로에는 `csrf()` 테스트 후처리기를 사용하지 않으므로 실제 토큰 발급·세션 연결·헤더 전달을 함께 확인한다.

검증 명령: `mvnw.cmd --batch-mode -Dtest=DemoSessionApiTests test`

결과: 기존 2개와 신규 2개, 총 4개 통과. 실패·오류·생략 0, BUILD SUCCESS. 기존 CI control-plane의 Maven verify가 이 테스트를 포함한다.

후속 전체 회귀 검증: `mvnw.cmd --batch-mode test`로 서버 전체 192개 테스트가 통과했다(46.819초, 실패·오류·생략 0). 이는 로컬 기본 테스트 설정의 결과이며 CI의 MySQL 복원 검증이나 운영 환경 검증을 대체하지 않는다. 실행 로그는 로컬 `work/server-full-regression-2026-09-16.log`에 보관했다.

실제 브라우저 쿠키 정책, SSO, 모든 변경 endpoint의 CSRF 경계 및 운영 전체 배포 흐름을 증명하지 않는다. 제품 권한 설정 변경은 없다.
