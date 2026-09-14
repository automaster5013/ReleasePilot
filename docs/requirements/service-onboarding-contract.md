# ReleasePilot MVP 서비스 등록 계약

## 1. 목적

서비스 등록은 ReleasePilot이 제어할 Kubernetes Rollout과 판정에 사용할 Prometheus 시계열을 하나의 Environment에 연결하는 과정이다. 등록 성공은 단순한 설정 저장이 아니라 실제 외부 리소스에 대한 읽기 검증 완료를 의미한다.

## 2. 카탈로그 계층

```text
Project
  └─ Service
       ├─ staging Environment
       └─ production Environment
```

- Project는 관련 서비스를 묶는 논리 단위다.
- Service는 하나의 배포 가능한 애플리케이션이다.
- Environment는 Service가 배포되는 Kubernetes 대상과 정책, 관측 설정의 결합이다.

## 3. 등록 입력

### Project

| 필드 | 필수 | 제약 |
|---|---:|---|
| key | 예 | 소문자 영문, 숫자, `-`; 전체 시스템에서 유일 |
| name | 예 | 1~100자 |
| description | 아니오 | 최대 500자 |

### Service

| 필드 | 필수 | 제약 |
|---|---:|---|
| projectId | 예 | 활성 Project |
| key | 예 | Project 안에서 유일 |
| name | 예 | 1~100자 |
| repositoryUrl | 예 | HTTPS Git 저장소 주소 |
| owner | 예 | 운영 책임 팀 또는 사용자 식별자 |

### Environment

| 필드 | 필수 | 설명 |
|---|---:|---|
| serviceId | 예 | 활성 Service |
| name | 예 | MVP에서는 `staging` 또는 `production` |
| clusterId | 예 | 사전에 등록된 ClusterConnection |
| namespace | 예 | Rollout이 존재하는 namespace |
| rolloutName | 예 | Argo Rollouts Rollout 이름 |
| containerName | 예 | 이미지 digest를 교체할 Rollout Pod template의 컨테이너 이름 |
| strategy | 아니오 | `CANARY`(기본값) 또는 `BLUE_GREEN` |
| stableServiceName | 예 | Canary stable 또는 Blue/Green active Kubernetes Service |
| canaryServiceName | 예 | Canary candidate 또는 Blue/Green preview Kubernetes Service |
| prometheusConnectionId | 예 | 사전에 등록된 PrometheusConnection |
| workloadLabelSelector | 예 | 대상 시계열을 제한하는 label 집합 |
| defaultPolicyVersionId | 예 | ACTIVE 상태 정책 버전 |

## 4. 연결 정보

### ClusterConnection

- 표시 이름
- Kubernetes API server 식별 정보
- 허용 namespace 목록
- 인증 secret reference
- 상태와 마지막 검증 시각

Kubeconfig 원문이나 ServiceAccount token은 일반 설정 테이블과 감사 payload에 저장하지 않는다. 애플리케이션은 secret reference만 보유한다.

### PrometheusConnection

- 표시 이름
- base URL
- 인증 secret reference
- query timeout
- 상태와 마지막 검증 시각

MVP에서 ClusterConnection과 PrometheusConnection은 Operator만 생성·변경할 수 있다.

## 5. 등록 시 사전 검사

Environment는 다음 검사를 모두 통과해야 ACTIVE가 된다.

1. Kubernetes API에 연결할 수 있다.
2. 지정 namespace가 허용 목록 안에 있다.
3. Rollout이 존재하고 `argoproj.io/v1alpha1` Rollout이다.
4. Rollout의 strategy가 Environment의 `CANARY` 또는 `BLUE_GREEN`과 일치한다.
5. stable/canary 또는 active/preview Service 참조가 등록 입력과 일치한다.
6. 두 Kubernetes Service가 존재한다.
7. Control Plane ServiceAccount가 Rollout get/watch/patch 권한을 가진다.
8. Prometheus의 ready endpoint와 query endpoint에 접근할 수 있다.
9. label selector로 최근 시계열을 조회할 수 있다.
10. stable과 canary를 구분하는 `release_track` label이 존재한다.
11. 기본 정책이 ACTIVE이고 의미 검증을 통과하며 Environment와 같은 전략을 사용한다.

검사 결과는 항목별 PASS, FAIL, WARNING으로 저장하고 사용자에게 보여준다. FAIL이 있으면 Environment는 `INVALID`, WARNING만 있으면 `ACTIVE_WITH_WARNINGS`가 될 수 있다.

## 6. 상태

Project와 Service:

```text
ACTIVE ↔ DISABLED
```

Environment:

```text
DRAFT → VALIDATING → ACTIVE
                    ↘ ACTIVE_WITH_WARNINGS
           실패 시 → INVALID

ACTIVE | ACTIVE_WITH_WARNINGS | INVALID → DISABLED
```

활성 릴리스가 존재하면 Environment의 Kubernetes 대상, Prometheus 연결, label selector 또는 기본 정책을 변경할 수 없다.

릴리스 요청 Web Console은 Environment 선택 시 최신 검증 시각, 전체 상태와 항목별 PASS/WARNING/FAIL을 표시한다.
최신 검증 결과 조회는 Environment가 속한 Service의 Project 권한을 다시 확인하며, 권한이 없으면 존재 여부를
노출하지 않도록 `ENVIRONMENT_NOT_FOUND`를 반환한다.
OPERATOR는 선택한 Environment를 CSRF 보호된 요청으로 수동 재검증할 수 있다. UI는 재검증 중 중복 요청을
차단하고 결과를 즉시 갱신하며, 최신 상태가 `ACTIVE` 또는 `ACTIVE_WITH_WARNINGS`가 아니면 릴리스 요청을
활성화하지 않는다.
수동 재검증은 실행자, 결과 상태와 `MANUAL` trigger를 포함하는 `ENVIRONMENT_REVALIDATED` 감사 이벤트를
업무 트랜잭션에 기록한다. OPERATOR는 선택 Environment의 감사 이력을 시간순으로 조회하며 Web Console은
실행자, 발생 시각과 hash chain sequence를 표시한다.

## 7. workload label selector

ReleasePilot이 모든 Prometheus 시계열을 검색하지 않도록 Environment는 고정 label 집합을 가진다.

```json
{
  "service_namespace": "releasepilot-demo",
  "service_name": "checkout"
}
```

- key와 value는 Operator가 등록한다.
- 허용 label key는 관측성 계약의 allowlist로 제한한다.
- 사용자가 PromQL 문자열을 직접 입력하지 않는다.
- Worker가 검증된 query template과 selector를 결합한다.

## 8. 변경과 재검증

- 외부 연결 또는 대상 리소스가 바뀌면 Environment를 다시 검증한다.
- 활성 Environment는 기본 6시간마다 자동 재검증한다. 다중 Control Plane Replica는 DB lease로 중복 실행을
  방지하며 실패 결과는 15분 뒤 재시도하고 10분 이상 멈춘 lease는 회수한다. 주기와 batch 크기는
  `ENVIRONMENT_REVALIDATION_*` 환경 변수로 조정할 수 있다.
- 최신 검증의 유효 기간도 같은 기본 6시간이며 `ENVIRONMENT_REVALIDATION_MAX_AGE`로 조정한다. 조회 API는
  `validUntil`을 반환하고 Web Console은 기한이 지난 Environment의 요청을 사전 차단한다. 서버는 스케줄러
  지연과 무관하게 릴리스 요청 시 다시 기한을 검사하고 `ENVIRONMENT_VALIDATION_STALE`로 거부한다.
- 릴리스 직전 Rollout UID가 마지막 등록 검증 시점과 다르면 자동 실행하지 않는다.
- 변경 결과는 이전 설정 전체를 덮어쓴 감사 payload가 아니라 변경된 필드 목록과 검증 결과로 기록한다.

## 9. MVP 인수 시나리오

### 정상 등록

Operator가 유효한 Rollout과 Prometheus 연결을 입력한다. 모든 필수 검사가 PASS가 되고 Environment가 ACTIVE가 된다.

### Rollout 전략 오류

대상이 Deployment이거나 등록한 전략과 실제 Rollout 전략이 다르면 검사가 FAIL하고 Environment가 INVALID가 된다.

### Blue/Green 정상 등록

Operator가 `BLUE_GREEN`, active Service와 preview Service를 등록한다. 실제 Rollout의 `blueGreen` 참조와
일치하고 단일 preview 분석 단계 정책이 ACTIVE이면 Environment가 활성화된다.

### 필수 label 누락

메트릭에 `release_track`이 없으면 stable과 canary를 비교할 수 없으므로 Environment가 INVALID가 된다.

### 권한 부족

읽기는 가능하지만 patch 권한이 없으면 등록은 실패한다. 검사 결과에 필요한 최소 RBAC verb와 resource를 표시한다.

### 리소스 교체

동일한 이름으로 Rollout이 재생성되어 UID가 바뀌면 릴리스 직전 검사에서 이를 탐지하고 자동 실행을 차단한다.
