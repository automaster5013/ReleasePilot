# ReleasePilot MVP 릴리스 계약

## 1. 목적

이 문서는 개발자가 릴리스를 요청한 시점부터 성공, 거부 또는 롤백으로 종료될 때까지 ReleasePilot이 보장해야 하는 사용자 관점의 동작을 정의한다. 내부 API나 데이터베이스 구조보다 우선하는 MVP의 제품 계약이다.

## 2. 참여자

| 역할 | 할 수 있는 일 |
|---|---|
| Viewer | 공개 demo Project의 릴리스 진행 상황과 정제된 판정 근거 조회 |
| Developer | 릴리스 요청 생성, 자신의 요청 조회, 진행 상황과 판정 근거 확인 |
| Approver | 대기 중인 릴리스 승인 또는 거부, 결정 사유 기록 |
| Operator | 정책 관리, 릴리스 일시정지·재개·중단, 운영 상태 조사 |
| System | 사전 검사, 지표 수집·평가, 자동 승격·중단, 감사 이벤트 생성 |

한 사용자가 여러 역할을 가질 수 있다. MVP에서도 production 릴리스 요청자와 승인자는 달라야 한다. 로컬 개발 환경에서도 자기 승인 차단을 기본값으로 유지하고 테스트 fixture에서만 역할별 계정을 분리해 사용한다.

## 3. 릴리스 요청 입력

개발자는 다음 정보를 제출한다.

| 필드 | 필수 | 설명 |
|---|---:|---|
| Project | 예 | 릴리스가 속한 프로젝트 |
| Service | 예 | 배포 대상 서비스 |
| Environment | 예 | MVP에서는 `staging`, `production` |
| Version | 예 | 사람이 식별할 수 있는 버전 문자열 |
| Image digest | 예 | 변경 불가능한 컨테이너 이미지 식별자 |
| Change summary | 예 | 변경 목적과 주요 내용 |
| Commit SHA | 예 | 빌드의 원본 Git 커밋 |
| Pipeline URL | 예 | 빌드 및 테스트 결과 링크 |
| Requested policy | 아니오 | 생략하면 환경 기본 정책 사용 |

이미지 태그만으로 배포 대상을 식별하지 않는다. 요청 시점에 검증된 digest를 저장하며, 실제 실행 대상도 같은 digest여야 한다.

Web Console은 로그인 사용자가 접근 가능한 활성 Project와 Service만 선택지로 제공하고, 선택한 Service의
`ACTIVE` 또는 `ACTIVE_WITH_WARNINGS` Environment만 릴리스 대상으로 제공한다. 프로젝트 접근 권한이 없는
Service의 Environment 목록은 존재 여부를 노출하지 않고 `404`로 응답한다. 직접 UUID를 복사하는 절차 없이
Project → Service → Environment를 선택한 뒤 동일한 서버 측 요청 검증을 통과해야 한다.

## 4. 요청 접수 조건

ReleasePilot은 다음 조건을 만족할 때만 요청을 `PENDING_APPROVAL`로 전환한다.

- Project, Service, Environment가 활성 상태다.
- 대상 환경에 Kubernetes와 Argo Rollouts 연결 정보가 존재한다.
- 대상 서비스의 Rollout 리소스를 조회할 수 있다.
- 이미지 digest 형식이 유효하다.
- 동일 서비스·환경에 실행 중인 다른 릴리스가 없다.
- 적용할 정책이 활성 상태이며 버전이 고정되어 있다.
- 안정 버전이 식별 가능하다.

검사에 실패하면 요청은 실행되지 않는다. 사용자는 실패한 조건과 해결에 필요한 정보를 볼 수 있어야 한다.

## 5. 승인 계약

승인 화면에는 최소한 다음 정보가 표시된다.

- 서비스, 환경, 요청자, 요청 시각
- 현재 안정 버전과 신규 버전
- Commit SHA, 변경 요약, 파이프라인 링크
- 적용할 정책 버전
- Canary 단계와 각 단계의 관찰 시간
- 오류율, 지연 시간, 최소 요청 수 임계값

승인자는 승인 또는 거부 사유를 기록한다. 승인 후에는 릴리스 입력, 정책 버전과 Canary 단계가 변경되지 않는다. 변경이 필요하면 기존 요청을 취소하고 새 요청을 만들어야 한다.

릴리스 상세 API와 Web Console은 승인 검토를 위해 Service·Environment 이름, 요청자 표시명과 계정,
이미지 repository/digest, 변경 요약·Commit SHA·Pipeline URL, 불변 PolicySnapshot의 전략·단계·지표 임계값을
함께 표시한다. 정책 정보는 현재 활성 정책을 다시 조회하지 않고 요청 시점 snapshot을 사용한다.

## 6. MVP Canary 실행 계획

production 기본 계획은 다음과 같다.

| 단계 | Canary 트래픽 | 최소 관찰 시간 | 정상 판정 시 |
|---:|---:|---:|---|
| 1 | 10% | 5분 | 30%로 승격 |
| 2 | 30% | 5분 | 60%로 승격 |
| 3 | 60% | 5분 | 100%로 승격 |
| 4 | 100% | 5분 | 릴리스 성공 처리 |

staging은 개발 속도를 위해 별도의 짧은 정책을 사용할 수 있지만, production 정책과 동일한 평가 의미를 유지한다.

## 7. 지표 평가 계약

각 단계에서 ReleasePilot은 안정 버전과 Canary 버전을 동일한 시간 창으로 비교한다.

### Blue/Green 실행 계획

Blue/Green Environment는 Argo Rollouts의 `activeService`, `previewService`와
`autoPromotionEnabled: false`를 사용한다. 새 이미지는 preview ReplicaSet에 배포되고 단일 100% preview
분석 단계가 PASS한 뒤 ReleasePilot의 promote 명령으로 active Service가 전환된다. FAIL이면 abort하여
active Service를 기존 stable ReplicaSet에 유지하고, INCONCLUSIVE이면 자동 전환하지 않고 일시정지한다.
Environment와 정책의 전략이 다르면 릴리스 요청 단계에서 거부한다.

MVP 필수 지표는 다음 세 가지다.

| 지표 | 목적 | production 기본 조건 초안 |
|---|---|---|
| HTTP 5xx 오류율 | 기능적 회귀 탐지 | Canary 절대 오류율 1% 미만, 기준 대비 증가 폭 0.5%p 이하 |
| HTTP p95 지연 시간 | 성능 회귀 탐지 | Canary p95 500ms 미만, 기준 대비 증가율 20% 이하 |
| 요청 수 | 표본 신뢰성 확보 | 단계별 Canary 요청 1,000건 이상 |

기본값은 제품 동작을 구체화하기 위한 초안이며, 서비스와 환경별 버전 관리 정책으로 저장한다.

각 평가는 다음 결과 중 하나를 반환한다.

- `PASS`: 모든 필수 규칙을 충족했다.
- `FAIL`: 하나 이상의 실패 규칙을 위반했다.
- `INCONCLUSIVE`: 데이터 누락, Prometheus 조회 실패 또는 표본 부족으로 판정할 수 없다.

평가 결과에는 정책 버전, 쿼리 시간 범위, 원시 집계값, 계산값, 임계값, 표본 수 및 판정 사유를 저장한다.

## 8. 승격·대기·롤백 규칙

### PASS

- 현재 단계의 평가 결과와 근거를 기록한다.
- Argo Rollouts에 멱등적인 승격 명령을 보낸다.
- 다음 단계가 시작되었음을 확인한 뒤 ReleasePilot 상태를 갱신한다.

### FAIL

- 추가 승격을 즉시 중지한다.
- Argo Rollouts에 abort 명령을 보낸다.
- 안정 버전으로 트래픽이 복구되었는지 확인한다.
- 확인에 성공하면 `ROLLED_BACK`, 확인할 수 없으면 `ROLLBACK_FAILED`로 종료한다.
- 실패한 규칙과 관측값을 사용자에게 표시한다.

### INCONCLUSIVE

- 자동 승격하지 않는다.
- 기본적으로 동일 단계에서 최대 10분간 추가 관찰한다.
- 추가 관찰 후에도 판정 불가이면 `PAUSED`로 전환하고 Operator의 판단을 요청한다.
- Prometheus 장애와 단순 표본 부족은 서로 다른 사유 코드로 기록한다.

## 9. 수동 조작

Operator는 실행 중인 릴리스를 일시정지, 재개 또는 중단할 수 있다.

- 수동 조작에는 사유가 필수다.
- 수동 승격은 MVP에서 허용하되, 실패 판정이 발생한 단계는 우회할 수 없다.
- 정책 실패를 무시하는 강제 승격은 MVP 범위에서 제외한다.
- 모든 수동 조작은 사용자, 시각, 이전 상태, 새 상태와 사유를 감사 이벤트로 남긴다.
- Web Console은 Promote, Pause, Resume, Abort마다 서버가 발급한 CSRF token과 16자 이상의 새
  `Idempotency-Key`를 보내며, 응답 전 중복 클릭을 차단한다.

## 10. 상태 모델

```text
DRAFT
  → VALIDATING
  → PENDING_APPROVAL
  → APPROVED
  → DEPLOYING
  → ANALYZING
  → PROMOTING
  → SUCCEEDED

분기:
VALIDATING       → VALIDATION_FAILED
PENDING_APPROVAL → REJECTED | CANCELED
ANALYZING        → PAUSED | ROLLING_BACK
PAUSED           → ANALYZING | ROLLING_BACK
ROLLING_BACK     → ROLLED_BACK | ROLLBACK_FAILED
실행 상태         → FAILED
```

상태 변경은 허용된 전이만 가능하며, 모든 전이는 원인과 연관 ID를 가진 감사 이벤트를 생성한다.

## 11. 동시성과 멱등성

- 한 Service와 Environment 조합에는 동시에 하나의 활성 릴리스만 존재할 수 있다.
- 같은 요청의 재전송은 새로운 릴리스를 만들지 않는다.
- promote, abort, pause, resume 명령은 재시도해도 동일한 최종 효과를 가져야 한다.
- ReleasePilot 재시작 후 Kubernetes와 Argo Rollouts의 실제 상태를 다시 조회하여 실행을 복구한다.
- 외부 상태와 내부 상태가 다르면 자동 승격을 중단하고 조정 결과를 기록한다.

## 12. 감사 계약

최소 감사 대상은 다음과 같다.

- 릴리스 생성과 입력값
- 사전 검사 결과
- 승인·거부·취소와 사유
- 적용된 정책 및 정책 버전
- 각 단계 시작과 종료
- Prometheus 쿼리 및 평가 결과
- 자동·수동 승격, 일시정지, 재개, 중단
- Argo Rollouts 명령과 응답
- 최종 성공, 실패 또는 롤백 결과

감사 이벤트는 애플리케이션의 일반 로그와 분리해 MySQL에 저장한다. 이벤트는 수정하지 않고, 정정이 필요하면 새로운 정정 이벤트를 추가한다.

## 13. 사용자에게 보이는 완료 조건

### 성공

- 모든 Canary 단계가 PASS다.
- 신규 버전에 트래픽 100%가 전달된다.
- Argo Rollouts가 Healthy 상태다.
- 최종 관찰 단계가 PASS다.
- ReleasePilot이 `SUCCEEDED` 이벤트를 기록한다.

Web Console은 로그인 직후 사용자가 조회할 수 있는 최근 릴리스를 최신순으로 제공한다. 사용자는 UUID를
외부에서 복사하지 않고 버전·상태·요청일을 보고 릴리스를 선택할 수 있으며, 새 릴리스 생성 직후 목록과
상세 화면이 함께 갱신된다. 목록 새로고침은 현재 선택과 진행 중인 SSE 연결을 임의로 변경하지 않는다.

### 안전한 롤백

- FAIL 또는 운영자 중단이 기록된다.
- 안정 버전으로 트래픽이 복구된다.
- 안정 버전의 최소 health 조건이 확인된다.
- 어떤 정책과 지표 때문에 롤백했는지 타임라인에서 확인할 수 있다.

## 14. MVP 인수 시나리오

### 시나리오 A: 정상 릴리스

승인된 신규 버전이 모든 단계를 통과하고 100%로 승격된다. 사용자는 각 단계의 지표와 자동 판정 근거를 확인한다.

### 시나리오 B: 오류율 회귀

Canary 버전의 5xx 오류율이 임계값을 초과한다. ReleasePilot은 추가 승격 없이 abort하고 안정 버전 복구를 확인한다.

### 시나리오 C: 표본 부족

관찰 시간 동안 최소 요청 수를 확보하지 못한다. ReleasePilot은 자동 승격하지 않고 추가 관찰 후 릴리스를 일시정지한다.

### 시나리오 D: 승인 거부

승인자가 사유와 함께 요청을 거부한다. Kubernetes에는 어떤 변경도 발생하지 않으며 결정이 감사 로그에 남는다.
Web Console은 `PENDING_APPROVAL` 상태에서 APPROVER에게만 Approve/Reject를 표시하고, CSRF token과
멱등 키를 포함해 결정을 제출한 뒤 응답 상태를 즉시 화면에 반영한다. Production 요청자의 자기 승인은
`SELF_APPROVAL_NOT_ALLOWED` 안내로 표시한다.

### 시나리오 E: 제어 서버 재시작

Canary 진행 중 ReleasePilot이 재시작된다. 재시작 후 실제 Rollout 상태를 조회하여 중복 명령 없이 진행 상태를 복구한다.

## 15. 이번 문서에서 보류한 결정

다음 항목은 후속 설계에서 확정한다.

- AWS에서 사용할 트래픽 라우터: ALB, NGINX Ingress 또는 service mesh
- Prometheus 쿼리 템플릿과 서비스별 라벨 계약
- 인증 방식과 외부 Identity Provider 사용 여부
- 정책 표현 방식: 관계형 모델, JSON 문서 또는 제한된 DSL
- 감사 이벤트 보존 기간과 위변조 방지 수준
- staging의 기본 Canary 단계와 시간
