# ADR-004: stable과 canary 비교에 표준 관측 라벨을 사용한다

- 상태: 승인됨
- 결정일: 2026-09-14
- 대상: ReleasePilot MVP

## 배경

Argo Rollouts의 내부 ReplicaSet hash나 Pod 이름을 직접 Prometheus query에 사용하면 배포마다 query가 달라지고, 서비스별 계측 차이 때문에 정책을 재사용하기 어렵다. 사용자가 PromQL을 직접 입력하게 하면 injection, 고비용 query와 잘못된 비교의 위험도 생긴다.

## 결정

- stable과 canary는 `release_track` label로 구분한다.
- `release_track`은 애플리케이션 입력이 아니라 배포 및 관측 파이프라인이 부여한다.
- 값은 `stable`, `canary`만 허용한다.
- 서비스 선택에는 `service_namespace`, `service_name`, `deployment_environment`를 사용한다.
- Analysis Worker는 버전이 지정된 query template만 실행한다.
- Environment 등록 값은 allowlist 변수로만 template에 주입한다.
- 사용자가 임의 PromQL을 저장하거나 실행하는 기능은 MVP에서 제외한다.

## 결과

- 동일 정책과 query template을 여러 서비스에서 재사용할 수 있다.
- Argo Rollouts 내부 revision 표현과 분석 로직을 분리한다.
- 서비스 계측과 Collector 설정이 표준 계약을 따라야 한다.
- 트래픽 라우터 선택 시 stable/canary label 전달 방식을 반드시 검증해야 한다.

## 재검토 조건

- 선택한 AWS 트래픽 라우터에서 신뢰할 수 있는 release track 분리가 불가능한 경우
- native histogram으로 전환해 현재 bucket query 의미가 달라지는 경우
- 서비스 전체가 아닌 route 또는 tenant 단위 정책이 MVP 필수 요구사항이 되는 경우
