# ReleasePilot MVP 실행 백로그

## 1. 개발 원칙

- 수평적인 기반 기능을 모두 만든 뒤 연결하지 않고, 사용자 시나리오가 끝까지 동작하는 vertical slice로 개발한다.
- 각 단계는 자동 테스트, 관측 정보와 최소 문서를 포함해야 완료다.
- 외부 상태가 불명확한 경우 자동 승격하지 않는 동작을 정상 경로만큼 중요하게 테스트한다.
- production AWS 배포 전에 로컬 kind 또는 k3d 환경에서 같은 인수 시나리오를 통과한다.

## 2. 공통 완료 기준

모든 기능은 다음 조건을 충족해야 Done이다.

- 인수 조건을 자동 테스트 또는 재현 가능한 통합 테스트로 확인
- 주요 실패 경로에 안정적인 오류 코드 제공
- 상태 변경에 AuditEvent 생성
- 로그에 correlation ID 포함
- secret과 개인정보가 로그 및 감사 payload에 포함되지 않음
- API 변경이 OpenAPI 계약과 일치
- 로컬 실행 방법이 문서화됨

## 3. 단계별 백로그

### M0 — 저장소와 개발 기준선

예상: 1주

- 모노레포 디렉터리 및 공통 개발 명령 구성
- Spring Boot, Python Worker, Next.js 최소 프로젝트 생성
- MySQL 로컬 구성
- formatter, lint, unit test, secret scan을 포함한 CI
- ADR 및 문서 index
- Docker Compose 기반 애플리케이션 개발 환경

완료 조건:

- 새 개발자가 하나의 문서와 명령으로 세 애플리케이션을 실행한다.
- 빈 프로젝트의 CI가 전체 통과한다.
- 각 애플리케이션 health endpoint를 확인한다.

### M1 — 인증과 카탈로그

예상: 1.5주

- User, Role, ProjectMembership
- 서버 세션, CSRF, login/logout, demo session
- Project와 Service 생성·조회
- ClusterConnection과 PrometheusConnection secret reference
- Environment 생성과 검증 상태 모델

완료 조건:

- VIEWER, DEVELOPER, APPROVER, OPERATOR 권한 인수 시나리오가 통과한다.
- 잘못된 Rollout 또는 권한 부족을 항목별 검증 결과로 확인한다.

### M2 — 릴리스 요청과 승인 vertical slice

예상: 1.5주

- Release와 Artifact 저장
- 활성 릴리스 유일성 제약
- 정책 선택 및 PolicySnapshot
- 승인·거부와 자기 승인 차단
- 감사 타임라인 기본 UI
- idempotency 처리

완료 조건:

- Developer 요청 → Approver 승인 또는 거부가 UI에서 끝까지 동작한다.
- 승인 이후 artifact와 정책 변경을 차단한다.
- Kubernetes에는 아직 변경을 만들지 않는 slice다.

### M3 — Argo Rollouts 제어

예상: 2주

상태: 구현 및 로컬 실클러스터 검증 완료 (2026-09-14)

- Kubernetes Java Client 어댑터
- Rollout 시작, 관측, promote, pause, resume, abort
- Outbox 처리와 명령 멱등성
- Rollout UID/revision 검증
- 재시작 후 Reconciler 복구

완료 조건:

- 승인된 샘플 앱이 수동 판정으로 10→30→60→100 단계 진행한다.
- 중단 시 stable 복구를 확인한다.
- 제어 서버 재시작 후 중복 명령 없이 상태를 복구한다.

### M4 — 지표 분석과 자동 판정

예상: 2주

상태: 구현 및 로컬 통합 검증 완료 (2026-09-14)

- AnalysisJob lease와 재시도
- Python Prometheus client 및 query template 렌더링
- 요청 수, 5xx 오류율, p95 계산
- PASS/FAIL/INCONCLUSIVE 결정
- 자동 promote/abort/pause
- 판정 증거 UI

완료 조건:

- 정상 버전은 자동 승격된다.
- 오류율 회귀 버전은 자동 롤백된다.
- 표본 부족과 Prometheus 장애는 자동 승격되지 않는다.

### M5 — 관측성과 운영 UI

상태: 완료 (2026-09-14)

예상: 1주

- OpenTelemetry 계측
- Prometheus와 Grafana 대시보드
- Loki 로그 상관관계
- Release 진행 SSE
- 운영자용 상태 조정과 실패 조사 링크

완료 조건:

- 하나의 correlation ID로 API 요청, 분석 작업과 Rollout 명령을 추적한다.
- 릴리스 화면에서 각 단계와 판정 근거를 확인한다.

구현 메모: Control Plane의 Prometheus/OTLP 계측, Docker 로그의 Loki 수집, Grafana 프로비저닝,
Release SSE와 운영 콘솔을 연결했다. 실행 correlation ID는 rollout execution에서 analysis job과
outbox command로 복사되어 비동기 경계를 넘어 유지된다.

### M6 — 공개 데모 및 AWS

예상: 2주

상태: 완료 (2026-09-14)

- AWS 실행 환경과 트래픽 라우터 결정 및 구축
- GitHub Actions 이미지 빌드
- GitOps 저장소와 Argo CD 동기화
- `releasepilot.kr` DNS와 TLS
- VIEWER demo session 및 데이터 노출 검토
- 성공·실패 데모 시나리오와 자동 reset
- E2E, 부하 및 장애 테스트

완료 조건:

- HTTPS 공개 주소에서 읽기 전용 데모를 사용할 수 있다.
- 정상 릴리스와 자동 롤백을 반복 재현할 수 있다.
- 공개 계정으로 어떤 변경 API도 실행할 수 없다.

구현 상태(2026-09-14): AWS/EKS Auto Mode Terraform, ECR, Route 53/external-dns, TLS/Ingress,
Argo CD GitOps, digest 이미지 workflow, 클러스터 내 데모 MySQL과 VIEWER 전용 공개 세션을 구현했다.
Control Plane 통합 테스트는 공개 세션의 Abort 요청이 403임을 검증하며 k6 읽기 부하 시나리오와
운영 runbook을 추가했다. 로컬 컨테이너 대상 k6 검증은 10 VU/60초, 8,851회 반복에서 실패율 0%,
p95 43ms로 통과했다. Terraform validate, Kustomize render와 전체 애플리케이션 테스트도 통과했다.
GitHub OIDC로 ECR push와 digest 기반 main 갱신을 수행하고 Argo CD가 새 digest를 동기화한다.
`releasepilot.kr` DNS, Let's Encrypt TLS와 세 Rollout의 정상 승격을 실제 EKS에서 확인했다. 공개
VIEWER 세션의 상태 변경 거부는 통합 테스트와 실환경 요청으로 검증한다.

### M7 — 포트폴리오 마감

예상: 1주

상태: 완료 (2026-09-14)

- README와 아키텍처 다이어그램
- 의사결정과 트레이드오프 설명
- 3~5분 데모 스크립트 또는 영상
- 운영 runbook과 알려진 제한
- 비용 및 보안 점검

완료 조건:

- 처음 보는 사람이 제품 가치, 설계 경계와 실패 안전성을 이해한다.
- 저장소 문서만으로 로컬 데모를 재현할 수 있다.

## 4. 전체 예상 일정

개인 개발, 주당 15~20시간 기준으로 총 12주가 목표다. Kubernetes, AWS 또는 프런트엔드 경험에 따라 14주까지 완충 기간을 둔다.

| 주차 | 목표 |
|---|---|
| 1 | M0 |
| 2~3 | M1 |
| 4 | M2 |
| 5~6 | M3 |
| 7~8 | M4 |
| 9 | M5 |
| 10~11 | M6 |
| 12 | M7 |

## 5. MVP 출시 차단 조건

다음 중 하나라도 남아 있으면 공개 데모 출시를 보류한다.

- VIEWER가 상태 변경 API를 실행할 수 있음
- Prometheus 또는 Kubernetes 장애에서 자동 승격함
- rollback 완료를 실제 stable 상태 확인 없이 표시함
- secret이 저장소, 로그 또는 감사 이벤트에 노출됨
- 동일 Environment에서 두 릴리스가 동시에 실행됨
- demo reset 없이는 실패 시나리오를 반복할 수 없음
- HTTPS 또는 기본 보안 header가 적용되지 않음

## 6. MVP 이후 우선순위

1. Blue/Green 전략 — 완료 (2026-09-14)
2. OIDC/SSO — 완료 (2026-09-14, 공급자 연결은 배포 설정)
3. Slack 또는 GitHub Checks 알림·승인 연동 — GitHub Checks 완료 (2026-09-14, token 연결은 배포 설정)
4. 다중 클러스터 — 완료 (2026-09-14, 클러스터별 Environment 대상 지정 및 일괄 권한 검증)
5. route별 중요도 정책 — 완료 (2026-09-14, OpenTelemetry `http.route` 범위와 CRITICAL fail-closed 판정)
6. 감사 이벤트 해시 체인과 외부 보관 — 완료 (2026-09-14, SHA-256 전역 체인·검증 API·트랜잭션 전달함·S3 Object Lock WORM 보관)
7. 인증 endpoint rate limit — 완료 (2026-09-15, 로그인 IP·계정 및 demo session IP의 MySQL 공유 제한, 429/Retry-After 계약)
8. 사용자 활성 세션 관리 — 완료 (2026-09-15, 안전한 세션 참조 조회·선택/일괄 무효화·감사 기록)
9. 활성 세션 웹 콘솔 — 완료 (2026-09-15, 현재 세션 표시·개별/일괄 종료·공유 demo 보호 안내)
10. Environment 정기 재검증 — 완료 (2026-09-15, DB lease·실패 재시도·감사 이벤트·결과 메트릭)
