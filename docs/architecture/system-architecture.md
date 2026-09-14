# ReleasePilot MVP 시스템 아키텍처

## 1. 아키텍처 목표

ReleasePilot MVP는 다음 특성을 우선한다.

- 릴리스 판단 과정이 설명 가능하고 감사 가능하다.
- 외부 시스템 장애가 자동 승격으로 이어지지 않는다.
- 제어 서버가 재시작되어도 진행 중인 릴리스를 복구할 수 있다.
- 로컬 Kubernetes에서 개발한 구성을 AWS 공개 환경으로 옮길 수 있다.
- 포트폴리오 범위에서 운영 가능한 복잡도를 유지한다.

## 2. 시스템 컨텍스트

```text
Developer / Approver / Operator
              │
              ▼
       Next.js Web Console
              │ HTTPS / REST / SSE
              ▼
    Spring Boot Control Plane
       │       │         │
       │       │         └────────────── MySQL
       │       │
       │       └──────────────────────── Python Analysis Worker
       │                                      │
       │                                      └── Prometheus HTTP API
       │
       └────────────────────────────────── Kubernetes API
                                                   │
                                                   └── Argo Rollouts

GitHub Actions ──→ Container Registry
       │
       └─────────→ GitOps Repository ──→ Argo CD ──→ Kubernetes

Demo Service ──→ OpenTelemetry / Metrics / Logs ──→ Grafana Stack
```

## 3. 컨테이너별 책임

### Web Console — Next.js, TypeScript

- 릴리스 요청 및 승인 화면
- 진행 중인 Canary 단계와 상태 표시
- 기준 버전과 Canary 지표 비교
- 판정 근거 및 감사 타임라인 표시
- REST 명령 실행과 SSE 기반 상태 갱신

Web Console은 비즈니스 규칙을 판정하지 않는다. 권한과 상태 전이에 대한 최종 판단은 Control Plane이 수행한다.

### Control Plane — Java, Spring Boot

- 인증 사용자와 역할 검사
- 릴리스 요청 검증 및 승인 워크플로
- Release 상태 머신과 도메인 불변조건
- 정책 버전 스냅샷 생성
- 분석 작업 생성 및 결과 수신
- Argo Rollouts promote, pause, resume, abort 명령
- Kubernetes 실제 상태와 내부 상태 조정
- 감사 이벤트 기록

MVP에서는 배포 가능한 하나의 Spring Boot 애플리케이션으로 구성하되 내부 패키지는 모듈 경계를 갖는다.

### Analysis Worker — Python

- Control Plane에서 전달받은 분석 작업 수행
- Prometheus range query 실행
- 기준 버전과 Canary 버전의 동일 시간 창 비교
- 오류율, p95 지연 시간과 요청 수 계산
- 구조화된 평가 증거 반환

Worker는 승격 또는 롤백을 결정하거나 Kubernetes 명령을 실행하지 않는다. 정책 규칙별 계산 결과를 반환하고 최종 상태 전이는 Control Plane이 수행한다.

### MySQL

- 프로젝트와 서비스 설정
- 릴리스, 승인, 실행 단계
- 버전이 고정된 정책 스냅샷
- 지표 평가 결과와 계산 근거
- 추가 전용 감사 이벤트
- 비동기 작업을 위한 Outbox 레코드

Prometheus 원본 시계열 전체를 복제하지 않는다. 판정을 재구성할 수 있는 쿼리, 시간 창, 집계값과 임계값을 저장한다.

### Argo CD

- GitOps 저장소에 선언된 애플리케이션 리소스 동기화
- Rollout, Service, Ingress 또는 트래픽 라우터 설정 배포
- 선언 상태와 클러스터 상태의 드리프트 탐지

### Argo Rollouts

- 새 ReplicaSet 생성
- Canary 단계 및 트래픽 가중치 실행
- 승격, 일시정지, 중단과 안정 버전 복구
- Rollout 상태를 Kubernetes API로 제공

### Observability Stack

- Prometheus: 정책 판정에 사용할 메트릭 원천
- Grafana: 운영 대시보드
- Loki: 애플리케이션 및 플랫폼 로그
- OpenTelemetry: 메트릭, 로그 및 트레이스 계측 표준

Tempo 도입은 MVP 구현 시점에 선택한다. 분산 트레이싱이 데모 가치를 충분히 높이지 않으면 첫 공개 버전에서는 제외할 수 있다.

## 4. Control Plane 내부 모듈

```text
com.releasepilot
├─ identity       사용자, 역할, 인증 경계
├─ catalog        Project, Service, Environment
├─ release        릴리스 요청과 상태 머신
├─ approval       승인 및 거부
├─ policy         정책 정의, 버전, 평가 조정
├─ rollout        Kubernetes와 Argo Rollouts 어댑터
├─ analysis       분석 작업과 Worker 계약
├─ audit          추가 전용 감사 이벤트
└─ shared         ID, 시간, 오류, Outbox와 공통 기술 요소
```

모듈은 서로의 테이블을 직접 수정하지 않는다. 공개된 애플리케이션 서비스 또는 도메인 이벤트를 통해 협력한다.

## 5. 주요 실행 흐름

### 릴리스 생성과 승인

1. Developer가 이미지 digest와 변경 정보를 제출한다.
2. Control Plane이 서비스, 환경, 실행 중 릴리스와 정책을 검사한다.
3. 유효한 요청에 정책 스냅샷을 연결하고 승인을 대기한다.
4. Approver가 승인하면 승인 이벤트와 Rollout 시작 작업을 같은 트랜잭션에 기록한다.
5. Outbox 처리기가 외부 실행을 시작한다.

### Canary 단계 평가

1. Control Plane이 Argo Rollouts에서 목표 단계 도달을 확인한다.
2. 최소 관찰 시간이 지난 후 분석 작업을 생성한다.
3. Python Worker가 Prometheus를 조회하고 규칙별 증거를 반환한다.
4. Control Plane이 고정된 정책 스냅샷에 따라 PASS, FAIL 또는 INCONCLUSIVE를 결정한다.
5. PASS이면 promote, FAIL이면 abort, INCONCLUSIVE이면 재관찰 또는 pause한다.
6. 명령과 외부 상태 확인 결과를 감사 이벤트로 기록한다.

### 재시작과 상태 조정

1. Control Plane 시작 시 종료되지 않은 Release를 조회한다.
2. Kubernetes API에서 해당 Rollout의 UID, revision, 단계와 상태를 읽는다.
3. 내부의 마지막 확인 상태와 비교한다.
4. 일치하면 대기 중 작업을 재개한다.
5. 불일치하면 자동 승격을 금지하고 `RECONCILIATION_REQUIRED` 이벤트를 기록한다.

## 6. 통신 방식

| 구간 | MVP 방식 | 선택 이유 |
|---|---|---|
| Web → Control Plane | REST | 명령과 조회가 단순하고 OpenAPI 적용이 쉽다 |
| Control Plane → Web | SSE | 릴리스 진행 상태를 단방향으로 실시간 전달하기 충분하다 |
| Control Plane ↔ Worker | DB-backed job + HTTP callback 또는 polling | 별도 메시지 브로커 없이 재시도 가능한 비동기 처리 |
| Control Plane → Kubernetes | 공식 Kubernetes Java Client | 리소스 조회와 명령의 명시적 제어 |
| Worker → Prometheus | Prometheus HTTP API | 쿼리와 시간 범위를 판정 증거로 저장 가능 |

MVP에서는 Kafka나 RabbitMQ를 도입하지 않는다. MySQL Outbox와 작업 테이블로 필요한 내구성과 재시도를 제공한다.

## 7. 실패 안전 원칙

- 알 수 없는 상태에서는 자동 승격하지 않는다.
- 외부 명령은 idempotency key와 대상 Rollout UID/revision을 포함한다.
- HTTP 성공만으로 명령 완료를 판단하지 않고 실제 Rollout 상태를 재조회한다.
- Prometheus가 응답하지 않으면 FAIL이 아닌 INCONCLUSIVE로 분류하되 릴리스는 진행하지 않는다.
- 감사 이벤트 기록이 실패하면 상태 전이나 외부 명령을 확정하지 않는다.
- 정책은 승인 시점의 스냅샷을 사용해 진행 중 변경의 영향을 받지 않는다.

## 8. 배포 토폴로지

### 로컬 개발

- Docker Compose: MySQL 및 애플리케이션 단위 개발
- kind 또는 k3d: Argo CD, Argo Rollouts, Prometheus를 포함한 통합 환경
- 로컬 이미지 레지스트리 또는 GitHub Container Registry

### AWS 공개 데모

- 공개 도메인: `releasepilot.kr`
- TLS 인증서와 DNS 라우팅을 적용한다.
- Web Console과 Control Plane은 동일 출처에서 제공한다.
- 주소:
  - `releasepilot.kr`: 제품 소개 및 Web Console
  - `releasepilot.kr/api/v1`: Control Plane API
  - `grafana.releasepilot.kr`: 인증된 운영 대시보드 후보
- `api.releasepilot.kr` 분리는 외부 API client가 필요해질 때 재검토한다.
- 실제 DNS 레코드는 AWS 진입점과 TLS 전략을 결정한 후 생성한다.

도메인 등록 주문번호, 결제 정보와 기타 영수증 정보는 소스 저장소에 기록하지 않는다.

## 9. 보안 경계

- 브라우저는 Kubernetes, Prometheus 또는 Argo Rollouts에 직접 접근하지 않는다.
- Control Plane 전용 ServiceAccount에는 필요한 namespace와 Rollout 작업만 허용한다.
- Prometheus 조회 자격 증명은 Analysis Worker에만 제공한다.
- 이미지 digest, Rollout UID 및 revision으로 대상 바꿔치기를 방지한다.
- 공개 데모의 관리 기능은 인증 뒤에 두고, 읽기 전용 데모 계정을 별도로 제공한다.
- 비밀 값은 Git에 저장하지 않고 로컬 secret 또는 AWS 비밀 관리 기능을 사용한다.

## 10. MVP에서 의도적으로 제외하는 요소

- 마이크로서비스별 독립 배포
- 외부 메시지 브로커
- Event Sourcing 전체 적용
- 다중 Kubernetes 클러스터
- 범용 워크플로 엔진
- 사용자 정의 코드 실행형 정책
- 별도 데이터 웨어하우스

## 11. 후속 결정

다음 ADR에서 순서대로 확정한다.

1. AWS 실행 환경과 트래픽 라우터
2. 정책 저장 형식과 평가 계약
3. 인증 및 공개 데모 접근 모델
4. Control Plane과 Worker의 작업 전달 방식
5. 감사 이벤트 보존 및 위변조 방지 수준
