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
11. 운영자 수동 조작 Web Console 보강 — 완료 (2026-09-15, CSRF·멱등 키·사유 입력·중복 클릭 차단·Resume)
12. 승인·거부 Web Console 연결 — 완료 (2026-09-15, 역할/상태 기반 노출·사유·CSRF·멱등성·자기 승인 안내)
13. 릴리스 요청 Web Console 연결 — 완료 (2026-09-15, Developer 권한·입력 사전 검증·CSRF·멱등성·생성 후 자동 조회)
14. 릴리스 요청 카탈로그 선택 — 완료 (2026-09-15, 접근 가능한 활성 Project·Service와 검증된 Environment 연동·권한 은닉)
15. 승인 컨텍스트 상세 화면 — 완료 (2026-09-15, 대상·요청자·artifact·pipeline·불변 정책 단계/임계값 표시)
16. 최근 릴리스 탐색 Web Console — 완료 (2026-09-15, 권한 범위 목록·상태/버전 선택·새로고침·생성 직후 연결)
17. 권한 범위 릴리스 목록 정확성 — 완료 (2026-09-15, 권한 필터 후 limit·Service/Environment 대상 식별)
18. 릴리스 감사 타임라인 Web Console — 완료 (2026-09-15, 시간순 이벤트·actor·correlation·hash chain sequence 표시)
19. 감사 해시 체인 무결성 Web Console — 완료 (2026-09-15, OPERATOR 전용 전체 체인 검증·실패 이벤트 경고·수동 재검증)
20. 릴리스 요청 Environment 검증 근거 — 완료 (2026-09-15, 선택 환경의 최신 항목별 점검·검증 시각 표시와 프로젝트 권한 은닉)
21. Environment 수동 재검증 Web Console — 완료 (2026-09-15, OPERATOR 전용 CSRF 재검증·결과 즉시 갱신·실패 환경 요청 차단)
22. Environment 재검증 감사 이력 — 완료 (2026-09-15, 수동 실행자 감사 체인 기록·OPERATOR aggregate 조회·Web Console 이력 표시)
23. Environment 검증 만료 릴리스 차단 — 완료 (2026-09-15, 6시간 유효 기한 서버 강제·Web Console 사전 차단·안정 오류 코드)
24. 승인 시점 Environment readiness 재확인 — 완료 (2026-09-15, 승인 대기 중 만료·무효화 차단·Rollout 미예약 보장·복구 안내)
25. 승인 전 Environment readiness 가시성 — 완료 (2026-09-15, 최신 상태·유효 기한 표시·조회 실패 fail-closed·Reject 유지)
26. 승인 시점 Kubernetes 연결 재확인 — 완료 (2026-09-15, 비활성 연결 승인 차단·Rollout 미예약·운영자 복구 안내)
27. Rollout 시작 직전 readiness preflight — 완료 (2026-09-15, Environment·검증 기한·Cluster 재확인·외부 mutation 차단)
28. ClusterConnection 검증 만료 차단 — 완료 (2026-09-15, 요청·승인·Rollout 시작에서 6시간 유효 기한 강제·외부 mutation 차단)
29. ClusterConnection readiness 복구 안내 — 완료 (2026-09-15, 요청·승인 실패 원인별 Web Console 안내·운영자 재검증 경로)
30. PrometheusConnection 검증 lifecycle — 완료 (2026-09-15, ready·query 검사·상태/시각 전이·안정 failure code·감사 기록)
31. Prometheus readiness 릴리스 admission — 완료 (2026-09-15, 요청·승인·Rollout 시작에서 ACTIVE/6시간 신선도 강제·UI 복구 안내)
32. Prometheus 연결 재검증 Web Console — 완료 (2026-09-15, OPERATOR 전용 목록·신선도 표시·CSRF 재검증·VIEWER 조회 차단)
33. Kubernetes 연결 재검증 Web Console — 완료 (2026-09-15, OPERATOR 전용 목록·namespace 범위·신선도 표시·CSRF 재검증·VIEWER 조회 차단)
34. 외부 연결 등록 Web Console — 완료 (2026-09-15, Kubernetes·Prometheus 입력 사전 검증·secret reference·CSRF 생성·생성 후 목록 갱신)
35. 외부 연결 감사 이력 Web Console — 완료 (2026-09-15, OPERATOR aggregate 조회·actor·시각·체인 sequence·검증 상태/failure code 표시)
36. 외부 연결 설정·자격 증명 교체 — 완료 (2026-09-15, OPERATOR Kubernetes·Prometheus 편집·검증 상태 fail-closed 초기화·감사 기록·Web Console 재검증 안내)
37. 외부 연결 비활성화 lifecycle — 완료 (2026-09-15, OPERATOR disable·enable·재검증 전 admission 차단·감사 기록·Web Console 상태 제어)
38. 외부 연결 일괄 재검증 — 완료 (2026-09-15, Kubernetes·Prometheus 일괄 실행·DISABLED 제외·개별 감사 결과·Web Console 요약)
39. 외부 연결 검색·상태 필터 — 완료 (2026-09-15, 이름 검색·attention/active/disabled 필터·검증 만료 fail-closed 분류·빈 결과 안내)
40. 외부 연결 신선도 실시간 분류 — 완료 (2026-09-15, 정상 필터 신선도 강제·30초/화면 복귀 갱신·6시간 만료 경계 테스트)
41. 서버 검증 만료 경계 통일 — 완료 (2026-09-15, 요청·승인·실행 직전 정확히 6시간 경과 시 fail-closed·환경/Kubernetes/Prometheus 경계 회귀 테스트)
42. 개발자·승인자 readiness 실시간 갱신 — 완료 (2026-09-15, 모든 로그인 역할 30초/화면 복귀 갱신·요청/승인 버튼과 만료 안내 공통 시계·경계 회귀 테스트)
43. 릴리스 mutation 전송 직전 readiness 검사 — 완료 (2026-09-15, Enter 제출·승인 사유/CSRF 대기 중 만료 차단·조회 불가 fail-closed·거부 유지 회귀 테스트)
44. 릴리스 요청 중복 제출 즉시 차단 — 완료 (2026-09-15, 렌더 이전 동기 잠금·요청/새로고침 완료까지 유지·실패 후 잠금 해제 회귀 테스트)
45. 릴리스 상세 비동기 대상 격리 — 완료 (2026-09-15, 최신 조회만 반영·조회 중 이전 대상 조작 차단·승인 환경 ID 일치 강제·종료 SSE 지연 결과 차단)
46. 요청 환경 검증 결과 대상 격리 — 완료 (2026-09-15, 선택 전환 후 지연 검증/감사 결과 무시·수동 재검증 결과 ID 일치 강제·버튼/전송 직전 환경 ID 검사)
47. 환경 재선택 검증 응답 순서 보장 — 완료 (2026-09-15, A-B-A 선택 세대 격리·자동/수동 검증 최신 요청 우선·수동 감사 조회 선택 세대 검사·회귀 테스트)
48. 환경 수동 재검증 중 요청 fail-closed — 완료 (2026-09-15, 이전 정상 결과 즉시 제거·재검증/릴리스 요청 상호 배제 동기 잠금·실패 후 차단 유지·재시도 회귀 테스트)
49. 릴리스 조작 응답 대상 격리 — 완료 (2026-09-15, 승인/거부/운영 조작 조회 세대 snapshot·선택 변경 전송 차단·지연 상태/오류/감사 응답 무시·회귀 테스트)
50. 운영 문서 구현·배포 상태 동기화 — 완료 (2026-09-15, replica-weight Canary·수동 승격·세션 비활성 만료·외부 연동 활성화 조건·공개 메트릭 범위·감사 보존과 종료 제약 명시)
51. 공개 데모 CSP bootstrap 차단 수정 — 완료 (2026-09-15, 요청별 nonce·동적 렌더링·no-store·production script 정책 유지·보안 회귀 3개·v0.49.0 배포와 데모 진입 재검증; 실제 릴리스 상세 E2E는 빈 데이터로 미검증)
52. 세션 복원 상단 상태 표시 일치 — 완료 (2026-09-15, 인증 세션 기반 표시·로그아웃/stream 상태 분리·회귀 2개·v0.50.0 배포와 브라우저 새로고침 재검증)
53. 격리된 웹 브라우저 E2E 자동화 — 완료 (2026-09-15, production standalone·Chromium·API fixture·로그인/복원/상세/근거/감사/조회 거부/VIEWER 제한 4개 시나리오·3회 반복 통과·CI와 실패 artifact 연결; 실제 backend/운영 E2E와 구분)
54. 격리된 MySQL 백업·복원 훈련 자동화 — 완료 (2026-09-15, 실제 migration 22개·테이블 28개·합성 BINARY/Unicode/JSON/외래 키 데이터·gzip/SHA-256·손상 거부·전체 스키마/행/체크섬 비교·임시 자원 소유권 확인 cleanup·CI 연결; 운영 백업/RDS 전환과 구분)
55. 공개 메트릭 Ingress 경계 차단 — 완료 (2026-09-15, actuator health Exact allowlist·회귀 4개·공개 metrics/info 404·헬스/API 200·경로 우회 표본 차단·내부 metrics 200·GitOps 동기화 검증)
56. 비밀 전달 객체 문자열 노출 차단 — 완료 (2026-09-15, SecretMaterial toString 고정 마스킹·Optional/컬렉션/null/빈 값 회귀 3개·서버 전체 85개 테스트 통과; 코드 보강이며 공개 이미지 배포와 외부 비밀 관리자 활성화는 별도)
57. 비밀 전달 객체 JSON 직렬화 보호 — 완료 (2026-09-15, Jackson JsonIgnore·직접/Map/List JSON 토큰 제외 회귀 2개·접근자와 Worker 전달 유지·서버 전체 87개 테스트 통과; 코드 보강이며 공개 이미지 배포는 별도)
58. 분석 예외 재시도 기록 민감 정보 차단 — 완료 (2026-09-15, 예외 원문 대신 고정 코드 저장·재시도 지연 유지·최종 INCONCLUSIVE/PAUSE 검증·회귀 3개·서버 전체 90개 테스트 통과; 기존 기록 정리와 공개 이미지 배포는 별도)
59. 분석 자격 증명 누락 fail-closed — 완료 (2026-09-15, 비밀 참조 누락 값/공백/조회 예외에서 Worker 호출 차단·SECRET_UNAVAILABLE 재시도와 최종 PAUSE·익명/정상 토큰 유지·회귀 6개·서버 전체 96개 테스트 통과; 공개 이미지 배포는 별도)
60. 누적 비밀 보안 보강 배포 및 복구 경계 검증 — 완료 (2026-09-15, 56–59번 v0.51.0 반영·null/빈 값/공백/탭·개행·다음 예약 시도 자격 증명 복구 추가 검증·서버 전체 100개 테스트·CI/이미지 workflow success·세 Rollout 2/2 Healthy·공개 VIEWER/메트릭 경계 재검증; 실제 릴리스 목록은 비어 있어 운영 분석 E2E와 구분)
61. 분석 요청 구성 실패 재시도 및 안전 종료 — 완료 (2026-09-15, 잘못된 정책/누락 snapshot에서 Worker 호출 차단·고정 코드/300초 재시도·최종 INCONCLUSIVE/PAUSE·정책 복구 재조회·회귀 사례 10개·서버 전체 110개 테스트 통과; 실행 대상 조회/DB 장애와 공개 이미지 배포는 별도)
62. 분석 시도/PASS 예약 Prometheus readiness — 완료 (2026-09-15, ACTIVE/설정 유효기간 엄격 경계 검사·Worker 호출 전 및 PASS 결과 처리 시 재조회·연결 ID 변경 차단·재시도/최종 PAUSE·FAIL ABORT 유지·회귀 사례 15개·서버 전체 125개 테스트 통과; 예약 이후 mutation 경계와 공개 이미지 배포는 별도)
63. 예약된 승격 명령 실행 전 readiness — 완료 (2026-09-15, PROMOTE 실행 직전 Environment/Kubernetes/Prometheus 상태·검증 만료 검사·비밀 조회/외부 mutation/Step 통과/성공 감사 차단·정상/재검증 복구·PAUSE/ABORT 유지·회귀 13개·서버 전체 138개 테스트 통과; Outbox 재시도 제한과 공개 이미지 배포는 별도)
64. 분석 안전성 v0.52.0 공개 배포 — 완료 (2026-09-15, 61–63번 반영·구현/릴리스 CI/이미지 workflow 통과·기존 IAM 사용자 인증 복구·Canary 검증과 수동 승격·세 Rollout 2/2 Healthy·새 Pod 6개 재시작 0·VIEWER/CSP/공개 메트릭 재검증; 실제 릴리스 분석 E2E는 데이터 부재로 미검증)
65. 분석 DB/Outbox/제어/감사 통합 검증 — 완료 (2026-09-15, 별도 H2 DB·실제 Spring 서비스/JPA·외부 gateway만 mock·판정별 제어/감사·예약 후 비활성화/비밀 누락/예외 검증 6개·JSON JDBC 매핑 누락 수정·서버 전체 144개 테스트 통과; 실제 MySQL/운영 E2E와 공개 이미지 배포는 별도)
66. 실제 MySQL 분석 통합 검증 자동화 — 완료 (2026-09-15, 임시 MySQL 8.4·loopback 임의 포트·Flyway 22개/Hibernate validate·기존 통합 시나리오 6개·JSON_TYPE 5컬럼·소유권 cleanup·CI 단계 연결·H2 전체 verify 유지; 실제 외부 gateway/운영 E2E와 공개 배포는 별도)
67. 감사 해시 체인 DB 저장 검증 — 완료 (2026-09-15, 한글/중첩 JSON/시각 왕복·동일 JSON 재정렬·내용/이전 해시/순번 변조 탐지 5개·시각 마이크로초 정규화 수정·MySQL 통합 11개/H2 전체 149개 통과·임시 자원 정리; 기존 기록 복구/내구성/외부 아카이브/공개 배포는 별도)
68. 저장 매핑·감사 정밀도 v0.53.0 배포 — 완료 (2026-09-15, 소스 CI/이미지 workflow 성공·GitOps Synced/Healthy·Canary 검증과 수동 승격·세 Rollout 2/2 Healthy·새 Pod 6개 digest 일치/재시작 0·공개 헬스/보안 헤더/VIEWER 재검증; 운영 기존 감사 복구와 실제 릴리스 E2E는 별도)
69. 감사 체인 끝값·부분 해시 누락 탐지 — 완료 (2026-09-15, head 잠금/끝값 비교·마지막 기록 삭제/head 부재/끝값 변조/해시 제거 거부·legacy 제외 유지·회귀 6개·H2 전체 155개/MySQL 통합 17개 통과·API 설명 동기화; 공개 배포/외부 보관본/동시성 부하는 별도)
70. 감사 기록·검증 동시성 기능 검증 — 완료 (2026-09-15, 실제 서비스/트랜잭션·동시 기록 8건 연속 순번/체인/아카이브 대기 확인·기록 commit 전 verifier 대기/후 일치·H2 전체 157개/MySQL 통합 19개 2회 통과·소유권 cleanup·CI helper 연결; 대규모 부하/첫 head 생성 경쟁/공개 배포는 별도)
71. 감사 verifier v0.54.0 공개 배포 — 완료 (2026-09-15, 소스 CI 6job/이미지 workflow 성공·임시 Karpenter taint 해제 후 배치·Canary 검증/수동 승격·세 Rollout Healthy 2/2·새 Pod 6개 digest 일치/재시작 0·공개 헬스/보안 헤더/VIEWER 정상; 웹 잘못된 Server Reference 오류 표본 1건 기록, 운영 기존 감사 복구/실제 릴리스 E2E는 별도)
72. 감사 아카이브 실패·재시도 안전성 — 완료 (2026-09-15, 예외 메시지 저장 제거/고정 코드·PENDING 유지/복구 오류 제거·9번째 실패부터 300초 상한·회귀 12개/서버 전체 169개 통과; 기존 오류 정리/실제 외부 전달/복수 Worker 중복 전송/공개 배포는 별도)
73. 감사 아카이브 재시도 DB 왕복 검증 — 완료 (2026-09-15, 실제 Worker/repository·외부 sink만 mock·실패 코드/시도/시각/PENDING 재조회·복구 DELIVERED/오류 제거/감사 체인 유지·미래 재시도 제외 2개·H2 전체 171개/MySQL 통합 21개 통과·소유권 cleanup·CI helper 유지; 외부 전달/재시작 내구성/공개 배포는 별도)
74. 아카이브 안전성 v0.55.0 공개 배포 — 완료 (2026-09-15, 소스 CI/이미지 workflow 성공·자동 taint 해제 후 Canary 검증·수동 승격·세 Rollout Healthy 2/2·새 Pod 6개 digest 일치/재시작 0/오류 표본 0·공개 헬스/보안 헤더/세션/VIEWER 정상; 실제 외부 실패 복구/운영 기존 감사/전체 릴리스 E2E는 별도)
75. HTTP 아카이브 로컬 장애·복구 검증 — 완료 (2026-09-15, loopback 실제 HTTP sink/Worker·429/500/503 실패 후 201 복구·고정 오류/PENDING/완료 상태·동일 PUT 경로/멱등 키/본문·합성 비밀 제외 3개·서버 전체 174개 통과; DB는 mock, 실제 S3/운영 외부 전달/timeout/중복 제거는 별도)
76. S3 SDK 경계 장애·복구 검증 — 완료 (2026-09-15, 403/500/503 후 복구·동일 요청/본문/조건부 쓰기/metadata 유지·합성 비밀 제외·412 충돌 미완료/읽기·삭제 없음 4개·서버 전체 178개 통과; SDK/repository mock, 실제 AWS/충돌 복구/복수 Worker는 별도)
77. HTTP 아카이브 timeout·중단 검증 — 완료 (2026-09-15, loopback 실제 10초 timeout 원인·중단 flag 보존·PENDING/고정 코드/재시도/미완료 확인 2개·서버 전체 180개 통과·서버/중단 상태 cleanup; repository mock, 응답 유실 후 원격 중복 제거/운영 네트워크는 별도)
78. 감사 아카이브 배치 실패 격리 검증 — 완료 (2026-09-15, 첫 sink 실패/이벤트 누락 후 다음 정상 항목 완료·각 상태/오류/시도 분리·빈 배치 조회/전송 생략 3개·서버 전체 183개 통과; repository/sink mock, DB rollback-only/프로세스 중단/복수 Worker는 별도)
79. 감사 아카이브 배치 sink 실패 격리 실제 DB 검증 — 완료 (2026-09-15, 실제 Worker/repository·sink mock·첫 실패 PENDING/고정 오류/재시도와 다음 DELIVERED 상태 DB 재조회·호출 순서/감사 체인 유지 1개·H2 전체 184개/MySQL 통합 22개 통과·Flyway 22개/JSON 타입 5개·소유권 cleanup; 테스트 트랜잭션 롤백, commit 후 재시작/DB rollback-only/이벤트 누락/복수 Worker/운영 외부 전달/E2E는 미검증, 새 운영 배포 없음)
80. 감사 아카이브 배치 commit 경계·새 Worker 복구 검증 — 완료 (2026-09-15, 실제 트랜잭션별 commit/별도 재조회·첫 실패와 다음 성공 상태 유지·새 Worker에서 실패 항목만 재전송/복구 commit·성공 항목 완료 시각/감사 체인 유지 1개·H2 전체 185개/MySQL 통합 23개·Flyway 22개/JSON 타입 5개/Ruff 통과·소유권 cleanup; sink mock, 실제 프로세스/DB 재시작·강제 종료/DB rollback-only/복수 Worker/외부 전달/운영 E2E는 미검증, 새 운영 배포 없음)
81. 감사 아카이브 배치 롤백·재전송 경계 검증 — 완료 (2026-09-15, 실제 SQL flush 후 명시적 rollback-only·별도 트랜잭션에서 실패/성공 상태 모두 원래 PENDING 복귀·성공 sink도 재호출·다음 배치 commit/감사 체인 유지 1개·H2 전체 186개/MySQL 통합 24개·Flyway 22개/JSON 타입 5개/Ruff 통과·소유권 cleanup; sink mock, 실제 DB 장애/commit 실패/원격 중복 제거/프로세스 중단/복수 Worker/운영 E2E는 미검증, 항목별 DB 장애 격리와 exactly-once 보장 없음, 새 운영 배포 없음)
82. 감사 아카이브 실제 DB·loopback HTTP 배치 통합 검증 — 완료 (2026-09-15, 실제 repository/Worker/HTTP sink·첫 503/다음 201 상태 commit·별도 재조회·새 Worker에서 실패 이벤트만 동일 PUT 경로/멱등 키/본문으로 복구 commit·성공 항목 완료 시각/감사 체인 유지 1개·H2 전체 187개/MySQL 통합 25개·Flyway 22개/JSON 타입 5개/Ruff 통과·HTTP 서버/소유 MySQL cleanup; 원격 영구 저장/중복 제거/실제 외부 전달/프로세스·DB 재시작/복수 Worker/운영 E2E는 미검증, 새 운영 배포 없음)
83. HTTP 수신 후 DB 롤백·멱등 재수신 통합 검증 — 완료 (2026-09-15, 실제 DB/HTTP sink·201 수신 저장 후 SQL flush/배치 롤백·PENDING 재조회·동일 요청 재전송/200 수신·대상 요청 2회/테스트 메모리 객체 1개·복구 commit/감사 체인/완료 후 추가 전송 없음 1개·H2 전체 188개/MySQL 통합 26개·Flyway 22개/JSON 타입 5개/Ruff 통과·소유 자원 cleanup; 실제 원격 영구 저장/중복 제거/충돌·응답 유실/DB 장애/재시작/복수 Worker/운영 E2E는 미검증, 새 운영 배포 없음)
84. HTTP 객체 충돌·배치 다음 항목 격리 통합 검증 — 완료 (2026-09-15, 실제 DB/HTTP sink·합성 기존 수신 객체 충돌 409/다음 정상 201·PENDING/고정 오류/1·2회 재시도 상태 commit·정상 항목 완료 유지·동일 요청/충돌 객체 보존/감사 체인 확인 1개·H2 전체 189개/MySQL 통합 27개·Flyway 22개/JSON 타입 5개/Ruff 통과·소유 자원 cleanup; 수신 객체는 테스트 메모리, 충돌 자동 복구/실제 원격 저장소/DB 장애·재시작/복수 Worker/운영 E2E는 미검증, 새 운영 배포 없음)
85. 감사 아카이브 JVM 종료 후 상태 유지·복구 검증 — 완료 (2026-09-15, 전용 테스트 클래스·같은 임시 MySQL/소유 UUID·seed JVM commit/정상 종료 후 다른 recover JVM에서 실패/성공 상태 재조회·재시도 전 전송 없음/실패 항목만 복구 commit·감사 체인 유지·H2 전체 190개/MySQL 기존 27개+분리 JVM 2개 통과·Flyway 22개/JSON 타입 5개/Ruff·소유 자원 cleanup·기존 CI helper 연결; sink mock, H2는 같은 JVM, 실제 DB 재시작/강제 종료/외부 전달/복수 Worker/운영 E2E는 미검증, 새 운영 배포 없음)
86. 이미지 release 소스 CI gate 보강 — 완료 (2026-09-15, 기존 CI workflow_call·같은 commit의 6개 검증 job·read-only validate·publish/GitOps success 의존 연결·실패 우회/다른 SHA/필수 job 누락 negative 회귀 포함 8개·Ingress 4개/Ruff/actionlint 통과·contracts CI 연결; 실제 release 실행·AWS 전달·운영 배포 없음, 병행 release/운영 healthy·smoke·복구 자동화는 별도)
87. 샘플 표시·모바일 상단 UI 개선 — 완료 (2026-09-15, 예시 안내/SAMPLE RELEASE·실제 상세 로드 시 SELECTED RELEASE 전환·상단 브랜드/세션 분리·로그인 버튼 스타일/focus·조회 버튼 한 줄/44px·320/390/800/1024px 회귀·웹 단위 34개/lint/typecheck/production build/E2E 9개 및 캡처 검수 통과; fixture UI, 역할별/전체 접근성/운영 E2E는 미검증, 공개 배포는 별도)
88. 역할별 UI 인수 테스트 확대 — 완료 (2026-09-15, DEVELOPER 카탈로그/요청·readiness 만료 차단·APPROVER 승인/거부·자기 승인 오류/만료·OPERATOR Promote/Pause/Resume/Abort·사유 취소 11개·fixture mutation CSRF/멱등 키/JSON/대상 확인·웹 단위 34개/lint/typecheck/새 production build/전체 E2E 20개 통과·기존 CI 연결; 역할 세션/API fixture, 실제 SSO/backend 권한/DB 감사/운영 E2E/전체 접근성/역할별 모바일은 미검증, 새 운영 배포 없음)
89. 역할별 UI 서버 거부·사유 입력 경계 검증 — 완료 (2026-09-16, 요청/승인 readiness 서버 거부·Abort 권한 거부에서 오류/성공 안내 없음/busy 해제·공백/1001자 사유 및 승인 취소에서 mutation 없음 8개·웹 단위 34개/lint/typecheck/새 production build/전체 fixture E2E 28개 통과; 제품 코드 변경 없음, 실제 backend 장애/SSO/운영 E2E/전체 접근성은 별도, 새 운영 배포 없음)
90. 역할별 모바일 UI 검수 — 완료 (2026-09-16, 320/390px DEVELOPER 요청·APPROVER 승인·OPERATOR 운영 버튼 배치/접수 6개·전체 가로 넘침 검사·운영자 연결 검증 헤더 세로 배치/버튼 줄바꿈/44px 수정·캡처 검수·웹 단위 34개/lint/typecheck/새 production build/전체 E2E 34개 통과; fixture UI, 실제 기기/전체 접근성/연결 조작/운영 E2E는 별도, 새 운영 배포 없음)
91. 역할 분리 SSO·2단계 정책 Rollout 운영 E2E — 완료 (2026-09-16, Cognito DEVELOPER 요청/별도 APPROVER 승인·20/100 각 60초·각 5개 규칙 PASS 후에만 자동 승격·두 DB step PASSED·최종 SUCCEEDED/네이티브 Healthy·전환 race 발견 및 839de77 수정·CI 35049863342/Docker Hub CD 35049863500/GitOps Synced·운영 3개 Rollout 2/2 Healthy·부하 Job cleanup; 1 replica 직접 stable/canary 요청이며 정확한 20% HTTP 분배는 traffic router 도입 전 미검증)
92. Docker Hub CD 입력 경로 제한 — 완료 (2026-09-16, apps/재사용 CI·CD workflow/GitOps 이미지 갱신 스크립트 push만 자동 게시·docs/deploy/계약 테스트 변경은 일반 CI만 실행·수동 전체 게시 유지·정확한 allowlist 계약 회귀; 구성요소별 선택 게시 최적화는 별도)
93. Docker Hub CD 구성요소별 선택 게시 — 완료 (2026-09-16, push diff 기반 동적 matrix·전역 입력/수동/영 before SHA 전체 게시·부분 digest 갱신·선택/보존 회귀 14개 통과·web-console 전용 run 35052615152에서 게시 job 1개/GitOps digest 한 줄만 변경·Argo Synced/Healthy·세 Rollout 2/2 Healthy·공개 HTTP 200 확인)
94. Docker Hub CD 배포 후 승인 경계 자동 검증 — 완료 (2026-09-16, GitOps 후 AWS OIDC·main/tag trust 제한·EKS DescribeCluster+이름 제한 Argo CRD get-only RBAC·선택 Rollout digest/Paused-or-Healthy/updated replica·Argo Synced·공개 readiness 15분 fail-closed polling·run 35053467003 최초 OIDC 거부 후 권한 수정/failed-job 재실행 성공·list/watch/mutation 없음·수동 Canary 승격 유지)
95. Docker Hub 이미지 취약점 차단 — 완료 (2026-09-16, Trivy Action v0.36.0 commit SHA 고정·게시 digest의 OS/library 스캔·수정 가능한 CRITICAL 발견 시 artifact/GitOps/배포 차단·provenance/SBOM 유지·정적 순서/옵션 회귀·run 35054627588에서 Tomcat/perl-base 6건 실차단·수정 후 CI 35055102360/CD 35055102462 성공·GitOps 43c49e5·수동 승격 후 세 Rollout Healthy 2/2·Argo Synced/Healthy·공개 readiness 200)
96. Docker Hub 이미지 provenance 발급·검증 게이트 — 완료 (2026-09-16, actions/attest v4 commit SHA 고정·OIDC/최소 job 권한·정확한 게시 digest를 Docker Hub OCI registry에 attest·같은 GitHub 저장소 발급 provenance 즉시 검증 후에만 artifact/GitOps 허용·정적 순서/권한 회귀 17개·CI 35056609472/CD 35056609769에서 세 이미지 검증 성공·GitOps 6287d64·수동 승격 후 세 Rollout Healthy 2/2·Argo Synced/Healthy·공개 readiness 200; Kubernetes admission 강제는 별도)
97. EKS provenance admission 강제 — 완료 (2026-09-16, Sigstore policy-controller 0.10.5/GitHub trust-policies v0.7.0·GitHub automaster5013/ReleasePilot와 Docker Hub releasepilot-*만 검증·MySQL registry 경계만 명시 예외·namespace label GitOps 영속화·현재 서명 digest/MySQL server dry-run 허용·이전 미서명 digest no-valid-bundles 실제 거부·정적 정책/manifest 회귀 포함 22개·controller 1/1·기존 세 Rollout Healthy 2/2·Argo Synced/Healthy 유지; 기존 Pod 소급 검증과 registry/검증 서비스 장애 시 가용성은 별도)
98. Admission webhook 노드·AZ 장애 내구성 — 완료 (2026-09-16, replica 2/failurePolicy Fail/PDB minAvailable 1·hostname+zone required anti-affinity 영속화·EKS Auto Mode가 ap-northeast-2a/2c 분산 노드 준비·준비 상태 webhook 노드 cordon+Pod 제거 중 서명 digest admission 지속 성공·반대 zone 대체 replica 복구 후 uncordon·미서명 digest 계속 거부·정적 fail-closed/중복/PDB/분산 회귀 포함 23개; 최초 affinity 전환의 짧은 fail-closed admission 유지보수 구간 문서화)
99. Provenance admission GitOps 소유권·자가 복구 — 완료 (2026-09-16, policy-controller 0.10.5/trust-policies v0.7.0 OCI chart와 Git values를 Argo CD 다중 소스로 선언·automated prune/selfHeal·두 Application Synced/Healthy·runtime 확장 webhook diff 경계·legacy Helm release Secret 7개 백업 후 제거/helm list 비움·관리 ConfigMap 삭제 후 새 UID 자동 복구·webhook 2/2 Ready·서명 digest 허용/이전 미서명 digest 거부·정적 회귀 24개; Application 자체는 클러스터 외부 1회 bootstrap 대상)
100. Argo CD Application app-of-apps 자가 복구 — 완료 (2026-09-16, `releasepilot-platform` 상위 앱 하나로 세 하위 Application 선언 관리·policy-controller/trust policy sync wave 순서·automated prune/selfHeal·기존 앱 tracking ID 인계·trust-policies Application 실제 삭제 후 새 UID 자동 재생성·정책 CR 무중단 유지·상위/하위 네 앱 Synced/Healthy·정적 회귀 25개; 상위 앱 하나는 클러스터 외부 최초 bootstrap 대상)
101. GitOps 상위 Application 부트스트랩 자동 복구 — 완료 (2026-09-16, 재실행 가능한 `bootstrap-gitops.ps1` 분리·Application CRD Established 대기·상위 앱 apply·네 Application Synced/Healthy 제한 시간 fail-closed 검사·플랫폼 bootstrap 자동 호출·상위 앱 실제 삭제 중 하위 앱 정상 유지/스크립트 실행 후 새 UID 복원·PowerShell 구문/정적 회귀 26개; 완전한 클러스터 재생성은 AWS 인프라·외부 비밀 복구 절차와 함께 수행)
102. 플랫폼 원격 설치 manifest 무결성 게이트 — 완료 (2026-09-16, Argo Rollouts 1.8.3/Argo CD 3.1.7/cert-manager 1.18.2/ingress-nginx 1.13.3 URL·SHA-256 고정·임시 download 후 digest 일치 파일만 원자적 승격·kubectl 원격 URL 직접 apply 제거·실제 4개 다운로드 digest 검증·기대 digest 변조 시 적용 파일 생성 없이 거부·PowerShell 구문/정적 회귀 27개; upstream 버전 갱신 시 공식 파일과 digest 동시 검토 필요)
103. GitHub Actions Node 24 전환·Docker Hub CD 직렬 실행 안정화 — 완료 (2026-09-16, 모든 외부 Action 40자 commit SHA 고정·checkout/setup 언어/artifact/buildx/AWS credentials Node 24 major 전환·고정 여부 회귀 포함 24개 통과·최신 main이 생긴 구식 CD의 배포 검증 생략 output 연결·정책이 digest로 변환한 MySQL 이미지와 PVC 런타임 상태의 Argo 비교 경계 제한·CI 35066365072/CD 35066365391 성공 및 Node 20 경고 0건·세 이미지 취약점/provenance/Docker Hub 게시·GitOps 6a54e08·Canary 검증/수동 승격·세 Rollout Healthy 2/2·네 Argo 앱 Synced/Healthy·공개 readiness 200; Node 24 액션 major 갱신 시 immutable SHA 재검토 필요)
104. 역할별 Web Console 접근성 CI 검수 — 완료 (2026-09-16, VIEWER/DEVELOPER/APPROVER/OPERATOR production fixture에 axe WCAG 2.0/2.1 A·AA 자동 검사·핵심 선택/조회/요청/승인/승격 Tab 도달과 visible focus 회귀 8개 추가·작은 단계/근거/감사/보조 설명 명암비 개선·Pipeline 링크 비색상 구분·웹 단위 35개/lint/typecheck/build/전체 E2E 102개 통과·CI 35068309624/CD 35068309888 성공·Web Console 단일 게시/provenance/취약점 게이트·GitOps dd8d02f·Canary 단계 승격 후 새 digest e8d8105 2/2 Healthy·네 Argo 앱 Synced/Healthy·공개 루트/health/readiness 200; 실제 스크린 리더/다중 브라우저·운영 SSO/backend 수동 검수는 별도)
105. 브라우저 엔진별 접근성 CI — 완료 (2026-09-16, Chromium 전체 102개 유지·Firefox/WebKit 역할별 WCAG A·AA 및 키보드 검사 각 8개 추가·CI에서 세 엔진 명시 설치/총 118개 통과·실패 report/trace lint 제외·로컬 Chromium+WebKit 110개 통과·CI 35069804438/CD 35069804815 성공·세 이미지 취약점/provenance 검증·GitOps 3281175·두 Canary 승인 후 세 Rollout 2/2 Healthy/Pod 재시작 0; 실제 스크린 리더/고대비·확대 도구·실기기 수동 검수는 별도)
106. 고대비·320px 리플로 접근성 CI — 완료 (2026-09-16, VIEWER/DEVELOPER/APPROVER/OPERATOR 강제 색상 모드에서 핵심 제어 표시·문서 가로 넘침 없음·320px 버튼 경계·Tab visible focus 검사 4개 추가·증거 표 내부 스크롤 격리 및 WebKit overflow 보완·단위 35개/lint/typecheck/build·로컬 Chromium 106개+WebKit 접근성 12개=118개 통과·CI 35071980684에서 Chromium/Firefox/WebKit 총 130개 통과·CD 35071980747 성공·Web Console 단일 게시/attestation/GitOps 2c72af6·digest f13a8e8 Canary 생성 후 다음 revision으로 대체; 실제 Windows High Contrast/스크린 리더/확대 도구 수동 검수는 별도)
107. WCAG 텍스트 간격·320px 리플로 접근성 CI — 완료 (2026-09-16, 네 역할에 WCAG 1.4.12 줄/글자/단어/문단 간격 강제 적용·핵심 제어 표시/320px 경계/내부 텍스트 잘림/문서 가로 넘침 없음 4개 추가·단위 35개/lint/typecheck/build·로컬 Chromium 110개+WebKit 접근성 16개=126개 통과·CI 35074202583에서 Chromium/Firefox/WebKit 총 142개 통과·CD 35074202893 성공·Web Console 단일 게시/취약점/provenance/GitOps 415c3b4·digest 257ea51 최종 stable 승격·세 Rollout 2/2 Healthy/Pod 재시작 0·네 Argo 앱 Synced/Healthy·공개 root/health/readiness 200; 실제 글꼴/확대 도구/스크린 리더 조합 수동 검수는 별도)
108. 동작 감소 접근성 CI — 완료 (2026-09-16, `prefers-reduced-motion: reduce`에서 smooth scroll 제거·애니메이션 반복 1회/애니메이션·전환 0.01ms 제한·VIEWER/DEVELOPER/APPROVER/OPERATOR 계산 스타일 검사 4개 추가·단위 35개/lint/typecheck/build·로컬 Chromium 114개+WebKit 접근성 20개=134개 통과·CI 35076012341에서 Chromium/Firefox/WebKit 총 154개 통과·CD 35076012547 성공·Web Console 단일 게시/취약점/provenance/GitOps a05eda5·digest 39b5c39 최종 stable 승격·세 Rollout 2/2 Healthy/Pod 재시작 0·네 Argo 앱 Synced/Healthy·공개 root/health/readiness 200; 브라우저/운영체제 자체 효과와 향후 영상 콘텐츠는 별도)
109. 320px 포인터 대상 크기 접근성 CI — 완료 (2026-09-16, 네 역할의 표시된 버튼/입력/선택/독립 링크를 WCAG 2.5.8 최소 24×24 CSS px로 측정·인라인 링크 예외 반영 4개 추가·단위 35개/lint/typecheck/build·로컬 Chromium 118개+WebKit 접근성 24개=142개 통과·CI 35077392346에서 Chromium/Firefox/WebKit 총 166개 통과·CD 35077392625 성공·Web Console 단일 게시/취약점/provenance/GitOps 17f7511·digest a3aa789 최종 stable 승격·세 Rollout 2/2 Healthy/Pod 재시작 0·네 Argo 앱 Synced/Healthy·공개 root/health/readiness 200; 실제 기기 물리 크기/손 떨림/보조 포인터 사용성은 별도)
110. 320px 키보드 포커스 가림 접근성 CI — 완료 (2026-09-16, 네 역할 전체 Tab 순회의 활성 요소를 viewport 교차 영역과 중앙 hit-test로 검사해 화면 밖·다른 요소 가림을 탐지하는 WCAG 2.4.11 회귀 4개 추가·브라우저 smooth scroll 안정화 및 엔진별 Tab 정책 반영·단위 35개/lint/typecheck/build·로컬 Chromium 122개+WebKit 접근성 28개=150개 통과·CI 35079573285에서 Chromium/Firefox/WebKit 총 178개 통과·CD 35079573223 성공·Web Console 단일 게시/취약점/provenance/GitOps 4b51c91·digest 0e5a9b0 최종 stable 승격·세 Rollout 2/2 Healthy/Pod 재시작 0·네 Argo 앱 Synced/Healthy·공개 root/health/readiness 200; 실제 브라우저 chrome·화면 확대 도구·가상 키보드 조합은 별도)
111. 키보드 포커스 외관 접근성 CI — 완료 (2026-09-16, 네 역할 전체 Tab 대상에 공통 2px/3px offset 고대비 `:focus-visible` 적용·표시/최소 두께/3:1 대비를 검사하는 WCAG 2.4.13 회귀 4개 추가·암시적 summary/스크롤 영역 포함·단위 35개/lint/typecheck/build·로컬 Chromium 126개+WebKit 접근성 32개=158개 통과·CI 35081357389에서 Chromium/Firefox/WebKit 총 190개 통과·CD 35081357865 성공·Web Console 단일 게시/취약점/provenance/GitOps f22fbe8·digest 41dbdca 최종 stable 승격·세 Rollout 2/2 Healthy/Web Pod 재시작 0·네 Argo 앱 Synced/Healthy·공개 root/health/readiness HTTPS 200; 복잡한 배경·운영체제 테마·확대 도구 조합은 실제 기기 수동 검수 별도)
112. 반복 탐색 건너뛰기 접근성 CI — 완료 (2026-09-16, 네 역할의 첫 Tab에 포커스 시 노출되는 본문 건너뛰기 링크·상단 브랜드/세션/인증 제어 우회·fragment와 실제 본문 DOM 포커스 이동·엔진별 링크 Tab 정책 통일을 검증하는 WCAG 2.4.1 회귀 4개 추가·단위 35개/lint/typecheck/build·로컬 Chromium 130개+WebKit 접근성 36개=166개 통과·CI 35083743613에서 Chromium/Firefox/WebKit 총 202개 통과·CD 35083743777 성공·Web Console 단일 게시/취약점/provenance/GitOps 89382b1·digest 23b443d stable 승격·세 Rollout 2/2 Healthy/Web Pod 재시작 0·네 Argo 앱 Synced/Healthy·공개 root/health/readiness HTTPS 200와 운영 HTML skip 링크 확인; 화면 낭독기별 landmark 탐색 경험은 실제 기기 수동 검수 별도)
113. 페이지 제목·랜드마크 계층 접근성 CI — 완료 (2026-09-16, 네 역할에서 고정 문서 제목·H1으로 이름 붙인 유일한 main·고유 이름을 가진 navigation·단일 H1을 접근성 트리로 검증하는 WCAG 1.3.1/2.4.2 회귀 4개 추가·단위 35개/lint/typecheck/build·로컬 Chromium 134개+WebKit 접근성 40개=174개 통과·CI 35088029774에서 Chromium/Firefox/WebKit 총 214개 통과·CD 35088030043 성공·Web Console 단일 게시/취약점/provenance/GitOps 17f733f·digest c0883e4 stable 승격·세 Rollout 2/2 Healthy/Web Pod 재시작 0·네 Argo 앱 Synced/Healthy·공개 root/health/readiness HTTPS 200와 운영 HTML 제목/랜드마크/CSP/HSTS 확인; 실제 화면 낭독기 landmark 단축키 경험은 기기별 수동 검수 별도)
