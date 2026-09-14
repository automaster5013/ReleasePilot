# ADR-001: Argo Rollouts를 Canary 실행 엔진으로 사용한다

- 상태: 승인됨
- 결정일: 2026-09-14
- 대상: ReleasePilot MVP

## 배경

ReleasePilot은 Kubernetes에서 애플리케이션을 직접 실행하는 플랫폼이 아니라, 릴리스 요청을 승인하고 운영 지표에 따라 다음 단계 진행 여부를 판단하며 그 과정을 감사할 수 있게 만드는 운영 통제 계층이다.

MVP에서 Canary와 Blue/Green을 동시에 자체 구현하면 트래픽 전환, 단계 관리, 실패 복구와 Kubernetes 리소스 상태 처리에 개발 역량이 분산된다. 실행 기능과 ReleasePilot이 제공해야 할 정책·승인·감사 기능의 경계도 흐려질 수 있다.

## 결정

1. MVP는 Canary 릴리스만 지원한다.
2. Argo Rollouts를 Canary 실행 엔진으로 사용한다.
3. Argo CD는 Git에 선언된 애플리케이션과 Rollout 리소스를 클러스터에 동기화한다.
4. Argo Rollouts는 Pod 교체, Canary 단계, 트래픽 전환, 승격 및 중단을 실행한다.
5. ReleasePilot은 릴리스 요청, 사전 검사, 승인, 정책 평가, `promote`/`abort` 결정과 감사 기록을 소유한다.
6. Prometheus 지표에 대한 비즈니스 판정은 ReleasePilot이 수행한다. Argo Rollouts의 AnalysisTemplate에 핵심 정책 판단을 위임하지 않는다.
7. Kubernetes와 Argo Rollouts 자체의 readiness, progress deadline, Pod 생성 실패 같은 런타임 안전장치는 그대로 활용한다.

## 책임 경계

| 구성요소 | 책임 |
|---|---|
| Argo CD | Git에 선언된 리소스의 동기화와 드리프트 탐지 |
| Argo Rollouts | Canary 단계 및 트래픽 전환 실행 |
| ReleasePilot | 승인, 정책 판정, 승격·중단 명령, 상태 타임라인, 감사 기록 |
| Prometheus | 안정 버전 및 Canary 버전의 운영 지표 제공 |
| Kubernetes | 워크로드 스케줄링, 상태 확인, 기본 복구 |

## 결과

### 장점

- 검증된 Progressive Delivery 실행 기능을 활용해 MVP 범위를 줄인다.
- ReleasePilot의 핵심 가치인 설명 가능한 판단과 감사 가능성에 집중할 수 있다.
- 이후 동일한 실행 엔진 위에서 Blue/Green을 확장할 수 있다.

### 비용과 제약

- Argo Rollouts가 필수 런타임 의존성이 된다.
- ReleasePilot과 Argo Rollouts 사이의 상태 불일치 및 명령 멱등성을 처리해야 한다.
- Argo CD가 관리하는 선언 상태와 ReleasePilot이 내리는 런타임 명령의 경계를 테스트해야 한다.

## 재검토 조건

다음 중 하나가 발생하면 이 결정을 재검토한다.

- Argo Rollouts가 요구하는 트래픽 라우터가 목표 AWS 환경과 맞지 않는 경우
- 외부 실행 엔진 의존성 때문에 핵심 감사 요구사항을 충족할 수 없는 경우
- 다중 클러스터 확장 시 제어 모델이 운영 불가능할 정도로 복잡해지는 경우
