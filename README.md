# ReleasePilot

ReleasePilot은 Kubernetes 애플리케이션의 배포 승인, Canary 진행, 운영 지표 검증, 자동 롤백과 감사 기록을 관리하는 DevOps 운영 플랫폼입니다.

> Kubernetes가 애플리케이션을 실행한다면, ReleasePilot은 그 애플리케이션을 안전하게 출시할 수 있는지를 판단하고 과정을 관리합니다.

## MVP 구성

- `apps/control-plane`: Java 21, Spring Boot Control Plane
- `apps/analysis-worker`: Python 기반 Prometheus 분석 Worker
- `apps/web-console`: Next.js, TypeScript Web Console
- `api/openapi`: Control Plane API 계약
- `policies`: 버전 관리형 릴리스 정책과 JSON Schema
- `config/observability`: 허용된 Prometheus query template
- `docs`: 요구사항, 아키텍처, ADR와 실행 백로그

## 개발 전제

- Java 21 이상
- Node.js 24 이상과 npm
- Python 3.13 또는 3.14
- Docker Desktop

## 로컬 실행

환경 변수 예제를 복사한 뒤 MySQL을 시작합니다.

```powershell
Copy-Item .env.example .env
docker compose up -d mysql
```

각 애플리케이션의 실행 방법은 해당 디렉터리 README를 참고합니다.

관측성 스택까지 함께 실행하려면 다음 overlay를 추가합니다.

```powershell
docker compose -f compose.yaml -f compose.observability.yaml up -d --build
```

- 운영 콘솔: `http://localhost:3000`
- Grafana: `http://localhost:3001`
- Prometheus: `http://localhost:9090`
- Loki: `http://localhost:3100`

Control Plane은 `/actuator/prometheus`를 공개하고 trace를 OTLP HTTP로 Collector에 전송합니다.
Docker 로그는 Promtail이 `service_name` label을 붙여 Loki에 보냅니다.

## 외부 연결 secret reference

Control Plane은 Kubernetes token이나 Prometheus token을 데이터베이스에 저장하지 않습니다.
로컬 MVP에서는 연결 등록 시 `secretRef`를 `env:환경변수명` 형식으로 지정하고,
해당 환경 변수에 Bearer token을 주입합니다. 예: `env:RELEASEPILOT_DEMO_KUBE_TOKEN`.
`vault:` 같은 운영용 reference는 이후 별도 `SecretResolver` 어댑터로 연결합니다.

## 기준 문서

- 제품 계약: `docs/requirements/release-contract.md`
- 시스템 아키텍처: `docs/architecture/system-architecture.md`
- 도메인 모델: `docs/architecture/domain-model.md`
- MVP 백로그: `docs/roadmap/mvp-backlog.md`

## 공개 주소

공개 데모 대상은 `https://releasepilot.kr`입니다. EKS Auto Mode, ECR, Route 53, ingress-nginx,
external-dns, cert-manager, Argo CD와 digest 기반 GitOps 정의는 `infra/aws`와 `deploy`에 있습니다.
계정 배포 절차와 검증/복구/종료 방법은 `docs/runbooks/public-demo.md`를 따릅니다. 현재 저장소에는
AWS 및 GitHub 인증 정보가 없으므로 실제 DNS와 클라우드 리소스는 아직 연결하지 않았습니다.
