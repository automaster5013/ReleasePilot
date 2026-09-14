# ReleasePilot MVP 정책 계약

## 1. 목적

정책은 특정 Canary 단계를 다음 단계로 승격해도 되는지를 결정하는 버전 고정형 규칙 모음이다. 정책은 임의 코드를 실행하지 않으며, 동일한 입력에 항상 동일한 결과를 반환해야 한다.

정책 형식의 기준 파일은 `policies/schema/release-policy.schema.json`이다.

## 2. 정책 생명주기

```text
DRAFT → ACTIVE → RETIRED
```

- DRAFT는 수정할 수 있지만 릴리스에 적용할 수 없다.
- ACTIVE는 새 릴리스에 적용할 수 있고 내용을 수정할 수 없다.
- RETIRED는 새 릴리스에 적용할 수 없지만 기존 Release의 PolicySnapshot은 계속 유효하다.
- 변경은 기존 버전을 수정하지 않고 새 PolicyVersion을 생성한다.
- 승인 시점에 schema validation을 통과한 전체 정책과 checksum을 PolicySnapshot으로 저장한다.

## 3. 스키마 외 의미 검증

JSON Schema 검증 후 Control Plane은 다음 의미 규칙을 검사한다.

- Canary weight는 앞 단계보다 커야 한다.
- 마지막 weight는 반드시 100이어야 한다.
- `HTTP_5XX_RATE`, `HTTP_P95_LATENCY_MS`, `REQUEST_COUNT`가 각각 정확히 한 번 존재해야 한다.
- 모든 MVP 필수 지표의 `required`는 true여야 한다.
- 오류율 threshold는 0 이상 1 이하여야 한다.
- REQUEST_COUNT에는 `relativeToBaseline`을 사용할 수 없다.
- p95 지연 시간의 단위는 millisecond다.
- maximumAbsoluteIncrease는 지표 원래 단위를 사용한다. 오류율 0.005는 0.5%p를 뜻한다.
- baseline 비교가 정의된 경우 절대 임계값과 baseline 임계값을 모두 통과해야 PASS다.

## 4. 지표별 계산 계약

### HTTP_5XX_RATE

```text
5xx 요청 수 / 전체 HTTP 요청 수
```

- 값의 범위는 0~1이다.
- baseline과 Canary를 동일한 관찰 시간 창에서 계산한다.
- 전체 요청 수가 0이면 INCONCLUSIVE다.

### HTTP_P95_LATENCY_MS

- Prometheus histogram bucket을 사용해 p95를 계산한다.
- 결과 단위는 millisecond다.
- histogram 데이터 또는 필요한 label이 없으면 INCONCLUSIVE다.

### REQUEST_COUNT

- 관찰 시간 창 안에서 Canary에 도달한 HTTP 요청 수다.
- threshold 미만은 FAIL이 아니라 `INSUFFICIENT_SAMPLE` 사유의 INCONCLUSIVE다.
- 추가 관찰이 끝난 뒤에도 부족하면 정책에 따라 PAUSE한다.

## 5. 전체 판정

규칙별 결과를 다음 우선순위로 결합한다.

1. 필수 규칙 중 FAIL이 하나라도 있으면 전체 FAIL
2. FAIL이 없고 필수 규칙 중 INCONCLUSIVE가 있으면 전체 INCONCLUSIVE
3. 모든 필수 규칙이 PASS이면 전체 PASS

초기 구현에는 선택 지표가 없지만 스키마는 향후 확장을 위해 `required`를 가진다.

## 6. 표준 사유 코드

| 코드 | 결과 | 의미 |
|---|---|---|
| THRESHOLD_EXCEEDED | FAIL | 절대 임계값 위반 |
| BASELINE_REGRESSION | FAIL | 안정 버전 대비 허용 회귀 폭 위반 |
| INSUFFICIENT_SAMPLE | INCONCLUSIVE | 최소 요청 수 미달 |
| PROMETHEUS_UNAVAILABLE | INCONCLUSIVE | Prometheus 조회 불가 |
| QUERY_TIMEOUT | INCONCLUSIVE | 제한 시간 안에 조회 미완료 |
| MISSING_SERIES | INCONCLUSIVE | 필수 시계열 또는 label 누락 |
| INVALID_RESULT | INCONCLUSIVE | NaN, 무한대 또는 예상하지 못한 결과 |
| ALL_RULES_PASSED | PASS | 모든 필수 규칙 충족 |

## 7. 판정 증거

MetricEvaluation에는 다음 값을 저장한다.

- metric key와 result
- reason code
- window start/end
- query template ID와 렌더링된 query hash
- baseline 및 Canary 계산값
- threshold와 baseline 회귀 제한
- 표본 수
- source Prometheus 식별자
- PolicySnapshot checksum

Prometheus 인증정보나 민감한 HTTP header는 증거에 포함하지 않는다.

## 8. 정책 예제

production 기본 예제는 `policies/examples/production-default-v1.json`에 저장한다. 이 값은 초기 제품 가설이며 부하 테스트 결과와 데모 인프라 용량을 바탕으로 조정할 수 있다. 조정 시에는 새 정책 버전을 만든다.
