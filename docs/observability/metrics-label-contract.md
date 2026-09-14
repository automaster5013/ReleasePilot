# ReleasePilot MVP 메트릭 및 라벨 계약

## 1. 목적

ReleasePilot이 stable과 canary의 오류율, 지연 시간과 요청 수를 동일한 의미로 비교하려면 서비스별로 공통된 메트릭 이름과 label이 필요하다. 이 문서는 샘플 애플리케이션과 ReleasePilot Analysis Worker 사이의 관측 계약이다.

## 2. 필수 메트릭

OpenTelemetry SDK 또는 Prometheus client가 다음 의미의 RED 메트릭을 제공해야 한다.

| 논리 이름 | Prometheus 계열 | 타입 | 단위 |
|---|---|---|---|
| HTTP 요청 수 | `http_server_request_duration_seconds_count` | Counter | 요청 |
| HTTP 응답 시간 | `http_server_request_duration_seconds_bucket` | Histogram | seconds |

5xx 수는 요청 수 계열에서 `http_response_status_code`가 500~599인 시계열을 집계한다. 계측 라이브러리 버전에 따라 실제 이름이 다를 수 있으므로 PrometheusConnection은 검증된 query template version을 참조한다.

## 3. 필수 label

| label | 예시 | 목적 |
|---|---|---|
| `service_namespace` | `releasepilot-demo` | 프로젝트 또는 namespace 범위 구분 |
| `service_name` | `checkout` | 서비스 구분 |
| `deployment_environment` | `production` | 배포 환경 구분 |
| `release_track` | `stable`, `canary` | 비교 대상 구분 |
| `service_version` | `1.4.2` | 표시 및 진단 |
| `http_response_status_code` | `200`, `503` | 오류율 계산 |
| `http_route` | `/api/orders/{id}` | 저카디널리티 route별 분석 |
| `le` | `0.5` | Histogram bucket 경계 |

필수 비교 label은 `release_track`이다. 값은 `stable` 또는 `canary`만 허용한다.

`pod`, container ID, trace ID, user ID, 전체 URL처럼 카디널리티가 높거나 일회성인 값은 정책 query selector에 사용하지 않는다.

## 4. release_track 부여 방식

애플리케이션이 자신을 stable 또는 canary로 판단하지 않는다. 배포 시스템이 Pod template에 label 또는 환경 변수를 주입하고, OpenTelemetry Collector 또는 메트릭 relabel 단계에서 `release_track` resource attribute를 Prometheus label로 변환한다.

권장 원천:

```text
Rollout이 관리하는 stable/canary Service 또는 ReplicaSet metadata
  → Pod label
  → OpenTelemetry resource attribute
  → Prometheus release_track label
```

실제 Argo Rollouts 트래픽 라우터를 선택할 때 자동 label 주입 가능 여부를 검증한다. 임의의 pod-template-hash를 정책에 직접 사용하지 않는다.

## 5. 쿼리 템플릿

아래는 의미를 설명하기 위한 템플릿이다. 사용자가 PromQL을 직접 제출하지 않으며 Analysis Worker가 allowlist label을 안전하게 렌더링한다.

### 요청 수

```promql
sum(
  increase(http_server_request_duration_seconds_count{
    service_namespace="<namespace>",
    service_name="<service>",
    deployment_environment="<environment>",
    release_track="<track>"
  }[<window>])
)
```

### 5xx 오류율

```promql
sum(increase(http_server_request_duration_seconds_count{
  service_namespace="<namespace>",
  service_name="<service>",
  deployment_environment="<environment>",
  release_track="<track>",
  http_response_status_code=~"5.."
}[<window>]))
/
sum(increase(http_server_request_duration_seconds_count{
  service_namespace="<namespace>",
  service_name="<service>",
  deployment_environment="<environment>",
  release_track="<track>"
}[<window>]))
```

### p95 지연 시간

```promql
histogram_quantile(
  0.95,
  sum by (le) (
    rate(http_server_request_duration_seconds_bucket{
      service_namespace="<namespace>",
      service_name="<service>",
      deployment_environment="<environment>",
      release_track="<track>"
    }[<window>])
  )
) * 1000
```

## 6. 시간 창과 데이터 품질

- stable과 canary는 같은 `windowStart`, `windowEnd`를 사용한다.
- 관찰 시작 직후 scrape 지연을 고려해 최소 30초의 stabilization delay를 둔다.
- 부분 응답, NaN, 무한대, 누락 시계열은 INCONCLUSIVE다.
- Counter reset은 `increase` 또는 `rate`가 처리하며 Worker가 원시 counter 차감으로 계산하지 않는다.
- p95 계산에는 동일한 histogram bucket 경계가 필요하다.
- stable 트래픽이 너무 적어 baseline을 계산할 수 없으면 baseline 비교 규칙은 INCONCLUSIVE다.

## 7. 경로별 분석 범위

MVP의 자동 판정은 서비스 전체 집계를 사용한다. `http_route`는 Grafana 진단과 판정 증거의 drill-down에만 사용한다.

다음 단계에서 경로별 critical route 정책을 추가할 수 있지만, 동적 URL이나 사용자 입력을 route label로 기록해서는 안 된다.

## 8. 로그와 트레이스 상관관계

필수 로그 필드:

- timestamp
- severity
- service.name
- service.version
- deployment.environment
- release.track
- trace_id와 span_id가 존재하는 경우 해당 값

정책 판정은 로그 또는 trace에 의존하지 않는다. Loki와 Tempo는 실패한 릴리스의 운영자 조사 링크를 제공한다.

## 9. 검증 기준

Environment 등록 시 최근 데이터로 다음을 확인한다.

- 모든 필수 메트릭 계열 존재
- 필수 label 존재
- `release_track` 값의 유효성
- Histogram bucket 존재 및 단조성
- query timeout 내 세 가지 템플릿 실행 완료
- 결과가 스칼라로 변환 가능

Canary가 아직 없는 최초 등록에서는 canary 시계열 누락을 WARNING으로 허용할 수 있다. 릴리스 실행 후 관찰 단계에서는 같은 누락을 INCONCLUSIVE로 처리한다.
