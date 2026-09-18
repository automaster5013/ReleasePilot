# ReleasePilot

[![CI](https://github.com/automaster5013/ReleasePilot/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/automaster5013/ReleasePilot/actions/workflows/ci.yml)
[![Docker Hub CD](https://github.com/automaster5013/ReleasePilot/actions/workflows/dockerhub-cd.yml/badge.svg?branch=main)](https://github.com/automaster5013/ReleasePilot/actions/workflows/dockerhub-cd.yml)

ReleasePilot은 Kubernetes 애플리케이션의 **배포 승인, Progressive Delivery, 운영 지표 판정, 자동 롤백, 감사 증거**를 하나의 흐름으로 관리하는 DevOps Control Plane입니다.

> Kubernetes가 애플리케이션을 실행한다면, ReleasePilot은 그 애플리케이션을 안전하게 출시할 수 있는지를 판단하고 그 과정을 증명합니다.

- 공개 쇼케이스: [https://releasepilot.kr](https://releasepilot.kr)
- 운영 콘솔: [https://releasepilot.kr/console](https://releasepilot.kr/console)
- 전체 작업 일정과 완료 기록: [docs/roadmap/delivery-timeline.md](docs/roadmap/delivery-timeline.md)
- 상세 백로그: [docs/roadmap/mvp-backlog.md](docs/roadmap/mvp-backlog.md) — **192/192 완료**

## 현재 상태

2026-09-19 기준 MVP와 후속 안정화 백로그를 모두 완료했습니다.

| 영역 | 현재 상태 |
| --- | --- |
| 제품 백로그 | 192/192 완료 |
| 운영 환경 | AWS 서울 리전 EKS Auto Mode, HTTPS 공개 운영 |
| 배포 방식 | Docker Hub digest → GitOps → Argo CD → Argo Rollouts Canary |
| Control Plane | Java 21 / Spring Boot, 205개 테스트 통과 |
| Web Console | Next.js / TypeScript, Chromium·Firefox·WebKit 회귀 검사 |
| 분석 Worker | Python, Prometheus 기반 정책 판정 |
| 보안 | 세션·CSRF·RBAC·secret redaction·gitleaks·provenance·admission 검증 |
| 감사 | SHA-256 체인, 변조 탐지, S3 Object Lock WORM 보관 |
| GitHub Checks | GitHub App installation token 자동 발급·갱신, 운영 Check Run 검증 완료 |

현재 운영 Control Plane 이미지는 GitOps에 digest로 고정되며, 최근 GitHub Checks 통합 배포는 `sha256:dad3db6359319a0688e7176dc52a122709d176ca305a798c457ee40e2aac26f9`입니다.

## 핵심 기능

- 역할 기반 릴리스 요청과 승인: VIEWER, DEVELOPER, APPROVER, OPERATOR 권한과 자기 승인 차단
- Canary·Blue/Green 제어: 시작, 관측, pause, resume, promote, abort와 실패 시 자동 롤백
- Fail-closed 사전 검사: Environment, Kubernetes, Prometheus 연결과 검증 신선도를 요청·승인·실행 직전에 재확인
- 정책 기반 자동 판정: 전역 지표와 OpenTelemetry `http.route`별 중요도를 평가하고 CRITICAL 회귀를 차단
- 다중 클러스터: Environment가 배포 대상을 고정하고 namespace별 Argo Rollouts 권한을 검증
- 감사 가능한 운영: correlation ID, append-only SHA-256 체인, 무결성 검증, JSON 보고서, 외부 WORM 보관
- GitHub Checks 연동: 승인·배포 결과를 Check Run으로 게시하고 GitHub App token을 자동 순환
- 운영 안전성: 멱등성, 동시성 잠금, timeout 복구, 지연 응답 격리, 접근성 있는 오류·포커스 복원
- 공개 쇼케이스: 승인 게이트, 단계별 트래픽 전환, 자동 롤백, 증거 체인과 변조 탐지를 브라우저에서 재현

## 아키텍처

```text
Developer / Approver / Operator
                │
                ▼
      Next.js Web Console
                │ same-origin session + CSRF
                ▼
   Spring Boot Control Plane ───────► GitHub Checks
       │          │       │
       │          │       └────────► S3 Object Lock audit archive
       │          └────────────────► Python Analysis Worker ─► Prometheus
       │
       └───────────────────────────► Kubernetes / Argo Rollouts

GitHub Actions ─► Docker Hub digest ─► GitOps commit ─► Argo CD ─► Canary rollout
```

모노레포의 주요 구성은 다음과 같습니다.

| 경로 | 역할 |
| --- | --- |
| `apps/control-plane` | 릴리스·승인·정책·Rollout·감사·GitHub Checks를 담당하는 Spring Boot Control Plane |
| `apps/analysis-worker` | Prometheus 질의를 실행하고 정책 판정 입력을 만드는 Python Worker |
| `apps/web-console` | 역할별 운영 흐름과 공개 쇼케이스를 제공하는 Next.js Web Console |
| `api/openapi` | Control Plane API 계약 |
| `policies` | 버전 관리형 릴리스 정책과 JSON Schema |
| `config/observability` | 허용된 Prometheus query template |
| `deploy` | Kubernetes base/overlay와 digest 기반 GitOps 정의 |
| `infra/aws` | EKS, 네트워크, DNS, 인증서 등 AWS 인프라 정의 |
| `docs` | 요구사항, ADR, 운영 검증, runbook, 백로그와 일정표 |

상세 설계는 [시스템 아키텍처](docs/architecture/system-architecture.md)와 [도메인 모델](docs/architecture/domain-model.md)을 참고하세요.

## 릴리스 안전 원칙

1. 외부 상태가 불명확하면 자동 승격하지 않습니다.
2. 승인 이후 artifact와 정책 snapshot은 변경할 수 없습니다.
3. Environment와 연결 상태는 요청 시점뿐 아니라 승인·실행 직전에도 다시 검증합니다.
4. CRITICAL route의 회귀는 전역 평균이 정상이어도 릴리스를 중단합니다.
5. 감사 이벤트는 같은 트랜잭션에서 체인과 외부 보관 전달을 예약합니다.
6. 이미지는 mutable tag가 아닌 digest로 Git에 기록하고 동일 산출물을 배포합니다.
7. ReleasePilot 자체도 20% → 60초 관찰 → 50% → 수동 승인 순서로 Canary 배포합니다.

## 로컬 개발

### 요구 사항

- Java 21 이상
- Node.js 24 이상과 npm
- Python 3.13 또는 3.14
- Docker Desktop

### 시작하기

환경 변수 예제를 복사하고 MySQL을 시작합니다.

```powershell
Copy-Item .env.example .env
docker compose up -d mysql
```

각 애플리케이션의 상세 실행 방법은 해당 디렉터리의 README를 따릅니다. 관측성 스택까지 실행하려면 다음 overlay를 추가합니다.

```powershell
docker compose -f compose.yaml -f compose.observability.yaml up -d --build
```

| 서비스 | 로컬 주소 |
| --- | --- |
| Web Console | `http://localhost:3000` |
| Grafana | `http://localhost:3001` |
| Prometheus | `http://localhost:9090` |
| Loki | `http://localhost:3100` |

Control Plane은 `/actuator/prometheus`를 제공하고 trace를 OTLP HTTP로 Collector에 전송합니다. Docker 로그에는 Promtail이 `service_name` label을 추가합니다.

## Secret과 외부 연결

Kubernetes·Prometheus token은 데이터베이스에 원문으로 저장하지 않습니다. 로컬에서는 `env:환경변수명` 형식의 `secretRef`를 사용하고, 운영에서는 projected volume과 전용 resolver로 자격 증명을 주입합니다.

GitHub Checks는 개인 access token 대신 저장소 전용 GitHub App을 사용합니다. Control Plane이 private key로 RS256 JWT를 서명해 단기 installation token을 발급하며, 만료 5분 전에 자동 갱신합니다. 설정과 키 교체 절차는 [GitHub Checks 연동 문서](docs/integrations/github-checks.md)에 있습니다.

## 테스트와 배포

모든 push는 다음 검증을 거칩니다.

- Java 단위·통합 테스트와 실제 MySQL 복원 훈련
- Python 테스트
- TypeScript lint, typecheck, production build와 브라우저 E2E
- OpenAPI·정책 계약 검사
- gitleaks secret scan
- 이미지 취약점·provenance 검증

CD는 변경된 애플리케이션 이미지만 게시하고 digest를 GitOps manifest에 기록합니다. Argo CD 동기화 후 새 Pod의 readiness, 이미지 digest, 오류 로그를 확인한 다음 Canary를 stable로 승격합니다.

## 문서 안내

- [전체 작업 일정과 완료 기록](docs/roadmap/delivery-timeline.md)
- [MVP 실행 백로그](docs/roadmap/mvp-backlog.md)
- [제품 계약](docs/requirements/release-contract.md)
- [정책 계약](docs/requirements/policy-contract.md)
- [시스템 아키텍처](docs/architecture/system-architecture.md)
- [공개 데모 운영 Runbook](docs/runbooks/public-demo.md)
- [3~5분 시연 스크립트](docs/runbooks/demo-script.md)
- [GitHub Checks 연동](docs/integrations/github-checks.md)

## 운영 범위와 다음 단계

공개 데모는 EKS 내부 단일 MySQL과 읽기 전용 VIEWER 세션을 사용합니다. 실제 프로덕션 전환 시에는 RDS, 전용 secret manager, WAF, 인증된 관측성 endpoint, 조직별 SSO 공급자 설정과 장기 비용·재해 복구 정책을 별도로 확정해야 합니다.

구현 순서, 배포 증거, CI/CD 실행과 운영 검증의 전체 흐름은 [작업 일정표](docs/roadmap/delivery-timeline.md)에 정리되어 있습니다.
