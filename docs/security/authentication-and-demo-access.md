# ReleasePilot MVP 인증·권한·공개 데모 계약

## 1. 목표

- `releasepilot.kr` 공개 방문자는 제품 흐름을 안전하게 살펴볼 수 있다.
- 릴리스 생성, 승인, 중단과 정책 변경은 인증된 사용자만 수행한다.
- production 승인자는 자신의 요청을 승인할 수 없다.
- 브라우저에 장기 API token을 저장하지 않는다.
- 로컬 개발과 AWS 공개 환경에서 같은 권한 의미를 유지한다.

## 2. 배포 및 인증 경계

MVP는 다음과 같은 단일 출처 구성을 사용한다.

```text
https://releasepilot.kr/
  ├─ /                 Next.js Web Console
  └─ /api/v1/*         Spring Boot Control Plane
```

Ingress 또는 AWS 진입점이 경로에 따라 Web Console과 Control Plane으로 전달한다. 브라우저는 HttpOnly 세션 쿠키로 인증한다. `api.releasepilot.kr` 분리는 향후 외부 API client가 필요할 때 재검토한다.

Grafana를 공개할 경우 `grafana.releasepilot.kr`로 분리하되 익명 관리자 접근을 허용하지 않는다.

## 3. 사용자 역할

| 역할 | 목적 | 주요 권한 |
|---|---|---|
| VIEWER | 공개 시연 및 읽기 전용 조사 | 공개 데모 Project의 릴리스·지표·정제된 감사 타임라인 조회 |
| DEVELOPER | 릴리스 요청자 | 허용 Project에서 릴리스 생성 및 조회 |
| APPROVER | 변경 승인자 | 릴리스 승인·거부, 전체 근거 조회 |
| OPERATOR | 플랫폼 운영자 | 환경·정책·연결 관리, pause/resume/abort, 감사 조회 |

권한은 역할만으로 결정하지 않고 사용자의 Project membership과 함께 검사한다. MVP의 OPERATOR는 모든 Project를 관리할 수 있는 플랫폼 역할로 둔다.

## 4. 인증 방식

### MVP

- Spring Security 기반 서버 세션
- 사용자 계정과 password hash는 MySQL 저장
- password hash는 Argon2id 또는 Spring Security가 지원하는 강한 adaptive hash 사용
- 인증 성공 시 Secure, HttpOnly, SameSite=Lax 세션 쿠키 발급
- 세션 ID는 로그인 성공 시 회전
- unsafe method에는 CSRF token 필수
- 로그아웃과 계정 비활성화 시 서버 세션 무효화

### 초기 계정 생성

- 최초 OPERATOR 계정은 배포 secret의 일회성 bootstrap 값으로 생성한다.
- bootstrap password는 저장소, 이미지, 로그 또는 감사 payload에 남기지 않는다.
- 최초 로그인 시 password 변경을 요구하고 bootstrap 기능을 비활성화한다.
- 데모 VIEWER는 비밀번호를 공개하는 공용 계정 대신 “읽기 전용 데모 시작” 동작으로 짧은 demo session을 발급한다.

도메인 로직은 인증 제공자가 아니라 내부 User ID와 Role만 참조한다.

### 조직 OIDC/SSO

조직 로그인은 표준 Authorization Code + OpenID Connect 흐름을 사용한다. 공급자가 검증한
`issuer + subject`를 `external_identities`의 불변 키로 연결하고, 이메일·이름·그룹 claim으로
기존 계정을 추측하거나 운영 권한을 부여하지 않는다. 역할과 프로젝트 멤버십은 ReleasePilot의
내부 `UserAccount`가 계속 유일한 권한 원천이다.

기본 설정은 `OIDC_ENABLED=false`, `OIDC_AUTO_PROVISION=false`다. 자동 프로비저닝을 명시적으로
활성화하면 `OIDC_ALLOWED_EMAIL_DOMAINS`에 포함된 검증 이메일만 새 계정으로 만들며 전역 역할은
항상 `VIEWER`다. 프로젝트 멤버십은 별도로 부여해야 한다. 비활성화된 내부 계정은 연결된 외부
ID가 유효해도 로그인할 수 없다.

공급자 연결 시 Spring Boot 표준 설정을 secret 또는 배포 환경 변수로 주입한다. 저장소에는
client secret을 넣지 않는다.

```properties
OIDC_ENABLED=true
OIDC_REGISTRATION_ID=releasepilot
SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_RELEASEPILOT_CLIENT_ID=...
SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_RELEASEPILOT_CLIENT_SECRET=...
SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_RELEASEPILOT_AUTHORIZATION_GRANT_TYPE=authorization_code
SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_RELEASEPILOT_REDIRECT_URI={baseUrl}/login/oauth2/code/{registrationId}
SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_RELEASEPILOT_SCOPE=openid,profile,email
SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_RELEASEPILOT_PROVIDER=releasepilot
SPRING_SECURITY_OAUTH2_CLIENT_PROVIDER_RELEASEPILOT_ISSUER_URI=https://idp.example.com
```

등록할 redirect URI는 `https://releasepilot.kr/login/oauth2/code/releasepilot`이다.

## 5. 공개 범위

### 익명 사용자가 볼 수 있는 정보

- 제품 설명
- 아키텍처 개요
- 정적인 데모 안내
- 로그인 또는 읽기 전용 데모 시작 화면

### VIEWER가 볼 수 있는 정보

- 별도로 지정된 demo Project
- 샘플 서비스와 환경의 표시용 정보
- 릴리스 상태, Canary 단계와 판정 결과
- 비밀이 제거된 감사 타임라인
- 사전 정의된 Grafana 패널 또는 애플리케이션 내부 차트

### VIEWER에게 숨기는 정보

- Cluster API 주소와 내부 namespace 세부정보
- repository가 비공개인 경우 전체 URL
- 사용자 이메일과 내부 식별 정보
- PromQL 원문에 포함될 수 있는 내부 topology
- connection secret reference
- 원본 감사 payload
- 운영 명령과 정책 변경 UI

## 6. 핵심 권한 규칙

| 동작 | VIEWER | DEVELOPER | APPROVER | OPERATOR |
|---|:---:|:---:|:---:|:---:|
| demo 릴리스 조회 | O | O | O | O |
| 허용 Project 릴리스 생성 | X | O | O | O |
| 릴리스 승인·거부 | X | X | O | O |
| pause·resume·abort | X | X | X | O |
| Project·Service 조회 | 제한 | 허용 범위 | 허용 범위 | 전체 |
| Environment 등록·검증 | X | X | X | O |
| 정책 생성·활성화 | X | X | X | O |
| 원본 감사 이벤트 조회 | X | X | 허용 범위 | 전체 |

추가 규칙:

- production 요청자는 자신의 릴리스를 승인하거나 거부할 수 없다.
- 비활성 사용자는 기존 세션이 있더라도 다음 요청부터 거부한다.
- VIEWER 세션은 demo Project 외 ID를 조회해도 존재 여부를 노출하지 않도록 404로 응답한다.
- 권한 실패와 자기 승인 시도는 감사 이벤트로 남긴다.

## 7. 세션 정책

- 일반 사용자 idle timeout: 30분
- 일반 사용자 absolute timeout: 12시간
- VIEWER demo session absolute timeout: 60분
- 동시 세션 수 제한은 MVP에서 강제하지 않지만 사용자별 세션 목록과 전체 무효화 기능을 고려한다.
- 서버 재시작 후에도 세션을 유지해야 하면 Spring Session JDBC를 사용한다.

## 8. 웹 보안 기준

- production은 HTTPS만 허용하고 HSTS를 적용한다.
- CSP, `frame-ancestors`, `X-Content-Type-Options` 등 기본 보안 header를 설정한다.
- 로그인과 demo session 발급에 IP 및 계정 기준 rate limit을 적용한다.
- 로그인 실패 응답은 계정 존재 여부를 구분하지 않는다.
- redirect 대상은 allowlist로 제한한다.
- 상태 변경 요청은 JSON만 허용하고 CSRF 검사를 통과해야 한다.
- 감사 payload와 로그에서 cookie, password, Authorization header와 secret을 제거한다.

## 9. 데모 데이터 안전성

- 공개 데모는 별도 namespace와 제한된 AWS 자원에서 동작한다.
- 실제 production 자격 증명이나 개인 데이터는 사용하지 않는다.
- 데모 릴리스는 allowlist 이미지와 샘플 서비스만 대상으로 한다.
- 방문자가 생성하는 릴리스 요청은 MVP 공개 버전에서 허용하지 않는다.
- 성공/실패 시연은 사전에 준비된 시나리오를 OPERATOR가 실행하거나 예약된 reset 작업으로 복원한다.

## 10. 인수 시나리오

### 읽기 전용 데모

익명 방문자가 demo session을 시작해 샘플 릴리스 타임라인을 조회하지만 abort API 호출은 403을 반환한다.

### 자기 승인 차단

APPROVER 역할도 가진 요청자가 자신의 production 릴리스를 승인하면 409 `SELF_APPROVAL_NOT_ALLOWED`를 반환하고 상태는 변하지 않는다.

### CSRF 차단

유효한 세션 쿠키가 있어도 CSRF token이 없는 승인 요청은 403을 반환한다.

### 계정 비활성화

Operator가 사용자를 비활성화하면 기존 세션의 다음 요청이 거부된다.

### 권한 범위 은닉

VIEWER가 demo Project가 아닌 release ID를 조회하면 404를 받아 리소스 존재 여부를 알 수 없다.
