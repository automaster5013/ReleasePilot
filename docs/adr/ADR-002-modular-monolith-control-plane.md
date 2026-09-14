# ADR-002: Control Plane은 모듈형 모놀리스로 시작한다

- 상태: 승인됨
- 결정일: 2026-09-14
- 대상: ReleasePilot MVP

## 배경

ReleasePilot은 승인, 릴리스 상태 머신, 정책 판정, Argo Rollouts 제어와 감사 기록 사이에 강한 일관성이 필요하다. 이를 초기부터 여러 Java 서비스로 분리하면 분산 트랜잭션과 이벤트 전달, 장애 복구 및 배포 운영 비용이 MVP의 가치보다 커진다.

Python은 Prometheus 시계열 처리와 통계 계산이라는 명확한 기술 경계가 있으므로 별도 Worker로 유지할 이유가 있다.

## 결정

- Java Control Plane은 하나의 Spring Boot 배포 단위로 시작한다.
- identity, catalog, release, approval, policy, rollout, analysis, audit를 내부 모듈로 구분한다.
- 각 모듈은 다른 모듈의 저장소나 테이블을 직접 수정하지 않는다.
- Python Analysis Worker만 별도 프로세스로 배포한다.
- Control Plane과 Worker의 비동기 작업은 MVP에서 MySQL 작업 테이블과 Outbox 패턴을 사용한다.
- 외부 메시지 브로커는 도입하지 않는다.

## 결과

- 상태 전이와 감사 기록을 하나의 로컬 트랜잭션으로 보호할 수 있다.
- 로컬 및 AWS 환경의 배포·관찰 복잡도가 낮아진다.
- 모듈 경계를 유지하면 필요할 때 Worker나 특정 모듈을 분리할 수 있다.
- 데이터베이스 작업 큐의 lease, 재시도와 중복 처리 규칙을 직접 구현해야 한다.

## 재검토 조건

- 분석 처리량이 Control Plane 데이터베이스에 지속적인 병목을 만드는 경우
- 독립 확장 또는 장애 격리가 필요한 모듈이 관측 데이터로 확인되는 경우
- 다중 클러스터 지원으로 명령 및 이벤트 처리량이 단일 배포 단위를 초과하는 경우
