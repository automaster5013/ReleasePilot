# ReleasePilot MVP 핵심 도메인 모델

## 1. 모델링 원칙

- Release가 사용자에게 보이는 작업 단위다.
- RolloutExecution은 외부 배포 엔진과의 실행 단위다.
- 승인 이후 입력과 정책은 변경하지 않는다.
- 현재 상태와 감사 이력을 분리해 모두 저장한다.
- 외부 리소스는 이름뿐 아니라 cluster, namespace, UID와 revision으로 식별한다.

## 2. 관계 개요

```text
Project
  └─ Service
       └─ Environment
            ├─ Default PolicyVersion
            └─ Release
                 ├─ ReleaseArtifact
                 ├─ ApprovalDecision
                 ├─ PolicySnapshot
                 ├─ RolloutExecution
                 │    └─ RolloutStep
                 │         └─ MetricEvaluation
                 └─ AuditEvent
```

## 3. Aggregate 경계

### Catalog Aggregate

#### Project

- `id`
- `key`
- `name`
- `status`

#### Service

- `id`
- `projectId`
- `key`
- `name`
- `repositoryUrl`
- `status`

#### Environment

- `id`
- `serviceId`
- `name`: staging 또는 production
- `clusterRef`
- `namespace`
- `rolloutName`
- `prometheusConnectionRef`
- `defaultPolicyVersionId`
- `status`

불변조건:

- 한 Service 안에서 Environment 이름은 유일하다.
- 활성 Environment는 조회 가능한 Rollout과 활성 정책을 가져야 한다.
- MVP의 Environment는 Kubernetes namespace 하나와 Rollout 하나를 가리킨다.

### Release Aggregate

#### Release

- `id`
- `serviceId`
- `environmentId`
- `requestedBy`
- `version`
- `changeSummary`
- `commitSha`
- `pipelineUrl`
- `status`
- `policySnapshotId`
- `stableArtifactId`
- `candidateArtifactId`
- `idempotencyKey`
- `createdAt`, `approvedAt`, `startedAt`, `finishedAt`
- `versionNumber`: 낙관적 잠금

#### ReleaseArtifact

- `id`
- `releaseId`
- `role`: STABLE 또는 CANDIDATE
- `imageRepository`
- `imageDigest`
- `displayTag`
- `commitSha`

#### ApprovalDecision

- `id`
- `releaseId`
- `decidedBy`
- `decision`: APPROVED 또는 REJECTED
- `reason`
- `decidedAt`

Release 불변조건:

- 동일 Service와 Environment에는 활성 Release가 하나만 존재한다.
- 요청자와 승인자는 production에서 동일할 수 없다.
- 승인 이후 candidate artifact와 PolicySnapshot은 변경할 수 없다.
- terminal 상태에서 다른 상태로 전이할 수 없다.
- Release 상태 전이는 release-contract에 정의된 전이만 허용한다.

### Policy Aggregate

#### Policy

- `id`
- `name`
- `scope`: GLOBAL, PROJECT, SERVICE 또는 ENVIRONMENT
- `scopeId`
- `status`

#### PolicyVersion

- `id`
- `policyId`
- `version`
- `definition`
- `createdBy`
- `createdAt`
- `status`: DRAFT, ACTIVE 또는 RETIRED

#### PolicySnapshot

- `id`
- `releaseId`
- `sourcePolicyVersionId`
- `definition`
- `checksum`
- `createdAt`

정책 버전은 활성화 후 수정하지 않는다. 변경은 새 PolicyVersion을 생성한다. Release 승인 시 해석이 끝난 전체 정의를 PolicySnapshot으로 복제한다.

### Rollout Execution Aggregate

#### RolloutExecution

- `id`
- `releaseId`
- `clusterRef`
- `namespace`
- `rolloutName`
- `rolloutUid`
- `targetRevision`
- `status`
- `currentStepIndex`
- `lastObservedResourceVersion`
- `startedAt`, `finishedAt`

#### RolloutStep

- `id`
- `executionId`
- `stepIndex`
- `targetWeight`
- `minimumObservationSeconds`
- `status`
- `startedAt`, `evaluationStartedAt`, `finishedAt`

RolloutExecution 불변조건:

- Release 하나에는 활성 RolloutExecution이 하나만 존재한다.
- promote 또는 abort 전에 Rollout UID와 target revision이 일치해야 한다.
- 완료된 Step의 판정은 변경하지 않고 재평가 시 새 평가 attempt를 생성한다.
- currentStepIndex는 Argo Rollouts에서 관측한 단계보다 앞설 수 없다.

### Analysis Aggregate

#### AnalysisJob

- `id`
- `releaseId`
- `rolloutStepId`
- `attempt`
- `status`: PENDING, RUNNING, COMPLETED 또는 FAILED
- `windowStart`, `windowEnd`
- `policySnapshotChecksum`
- `leasedBy`, `leaseExpiresAt`
- `createdAt`, `completedAt`

#### MetricEvaluation

- `id`
- `analysisJobId`
- `metricKey`
- `result`: PASS, FAIL 또는 INCONCLUSIVE
- `reasonCode`
- `queryTemplateId`
- `renderedQueryHash`
- `baselineValue`
- `canaryValue`
- `thresholdValue`
- `sampleCount`
- `evidence`
- `evaluatedAt`

#### AnalysisDecision

- `id`
- `analysisJobId`
- `result`: PASS, FAIL 또는 INCONCLUSIVE
- `reasonSummary`
- `decidedAt`

Python Worker는 MetricEvaluation 후보 결과를 계산한다. Control Plane은 결과의 스키마, 작업 ID, 정책 checksum과 시간 창을 검증하고 최종 AnalysisDecision을 확정한다.

### Audit Aggregate

#### AuditEvent

- `id`: 시간 순서가 보존되는 식별자
- `aggregateType`
- `aggregateId`
- `eventType`
- `actorType`: USER 또는 SYSTEM
- `actorId`
- `occurredAt`
- `correlationId`
- `causationId`
- `payload`
- `previousHash`와 `eventHash`: 확장 후보

감사 이벤트는 update 또는 delete하지 않는다. 개인정보나 secret, 인증 토큰, Prometheus 자격 증명은 payload에 저장하지 않는다.

## 4. Release 상태 전이 책임

| 현재 상태 | 명령/사건 | 다음 상태 | 결정 주체 |
|---|---|---|---|
| DRAFT | 요청 제출 | VALIDATING | Developer / Control Plane |
| VALIDATING | 검사 통과 | PENDING_APPROVAL | Control Plane |
| VALIDATING | 검사 실패 | VALIDATION_FAILED | Control Plane |
| PENDING_APPROVAL | 승인 | APPROVED | Approver |
| PENDING_APPROVAL | 거부 | REJECTED | Approver |
| APPROVED | 실행 시작 | DEPLOYING | Control Plane |
| DEPLOYING | 단계 도달 | ANALYZING | Reconciler |
| ANALYZING | PASS | PROMOTING | Policy Engine |
| PROMOTING | 다음 단계 도달 | ANALYZING | Reconciler |
| ANALYZING | FAIL | ROLLING_BACK | Policy Engine |
| ANALYZING | 판정 불가 지속 | PAUSED | Policy Engine |
| PAUSED | 운영자 재개 | ANALYZING | Operator |
| PAUSED | 운영자 중단 | ROLLING_BACK | Operator |
| ROLLING_BACK | 안정 버전 확인 | ROLLED_BACK | Reconciler |
| ROLLING_BACK | 복구 확인 실패 | ROLLBACK_FAILED | Reconciler |
| ANALYZING | 최종 단계 PASS | SUCCEEDED | Policy Engine / Reconciler |

## 5. 주요 도메인 서비스

### ReleaseSubmissionService

- 요청 입력 검증
- 활성 릴리스 중복 검사
- 안정 버전과 대상 Rollout 확인
- 정책 선택

### ApprovalService

- 승인 권한과 자기 승인 금지 검사
- PolicySnapshot 생성
- Release 승인 또는 거부

### ReleaseOrchestrator

- 실행 시작과 다음 작업 예약
- 상태 머신 명령 조정
- 분석과 Rollout 명령 연결

### PolicyDecisionService

- MetricEvaluation을 정책 규칙과 결합
- 전체 PASS, FAIL 또는 INCONCLUSIVE 결정
- 사람이 읽을 수 있는 판정 근거 생성

### RolloutReconciler

- Argo Rollouts 실제 상태 관측
- 내부 상태와 비교
- 재시작 후 실행 복구
- 불일치 시 fail-closed 처리

### AuditRecorder

- 사용자 및 시스템 행위 기록
- correlation/causation 관계 유지
- 민감 정보 제거

## 6. 저장 및 트랜잭션 원칙

- Aggregate 하나의 변경과 해당 AuditEvent, OutboxEvent를 동일한 MySQL 트랜잭션에 기록한다.
- 외부 시스템 호출은 데이터베이스 트랜잭션 안에서 실행하지 않는다.
- 외부 명령은 Outbox에서 전달하고 확인 결과를 별도 트랜잭션으로 반영한다.
- 동시 승격과 중단은 Release의 `versionNumber`로 충돌을 감지한다.
- Service/Environment별 활성 릴리스 유일성은 애플리케이션 검사뿐 아니라 데이터베이스 제약으로도 보호한다.

## 7. 식별자와 시간

- 내부 ID는 UUIDv7 또는 동등한 시간 정렬 가능 ID를 사용한다.
- 외부 API에는 순차 데이터베이스 ID를 노출하지 않는다.
- 모든 시간은 UTC로 저장하고 UI에서 사용자 시간대로 표시한다.
- Kubernetes resourceVersion은 정렬 가능한 도메인 버전으로 해석하지 않고 관측 토큰으로만 사용한다.

## 8. 아직 확정하지 않은 모델링 결정

- PolicyVersion `definition`의 JSON 스키마
- 읽기 모델을 별도 테이블로 둘지 여부
- 감사 해시 체인을 MVP에 포함할지 여부
- 사용자 및 인증 주체를 내부 관리할지 외부 IdP에 위임할지 여부
- 다중 승인 또는 승인 만료 정책
