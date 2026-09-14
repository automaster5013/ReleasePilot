# Route별 중요도 정책

서비스 전체 평균은 결제·주문 같은 핵심 route의 회귀를 숨길 수 있다. ReleasePilot 정책의 metric에는
선택적으로 OpenTelemetry `http.route` 템플릿과 중요도를 지정할 수 있다.

```json
{
  "key": "HTTP_5XX_RATE",
  "required": true,
  "comparison": "LESS_THAN",
  "threshold": 0.005,
  "relativeToBaseline": {"maximumAbsoluteIncrease": 0.002},
  "route": "/checkout/{id}",
  "importance": "CRITICAL"
}
```

- `route`는 원시 URL이 아니라 계측 라이브러리가 생성한 저카디널리티 `http.route` 템플릿과
  정확히 일치해야 한다.
- `CRITICAL`은 route가 반드시 있어야 하며 `required: true`여야 한다. Worker도 방어적으로
  CRITICAL rule을 항상 gating rule로 취급한다.
- `STANDARD` route rule은 `required` 값에 따라 판정 차단 여부를 결정한다.
- 전역 필수 metric 세 개는 그대로 한 번씩 존재해야 한다. Route rule은 전역 안전 기준을 대체하지 않고
  같은 metric key에 추가할 수 있으며 `(key, route)` 조합은 중복될 수 없다.
- route 값은 엄격한 allowlist 정규식으로 검증하고 PromQL 문자열을 정책에서 직접 받지 않는다.
  따라서 label matcher injection은 거부된다.

판정 증거에는 `route`, `importance`, route 전용 query template ID와 query hash가 저장된다. 콘솔은
전역 metric과 핵심 route metric을 같은 stable/canary 비교 표에 표시한다. CRITICAL route의 FAIL은
다른 전역 metric이 모두 PASS여도 전체 분석을 FAIL로 만들고 기존 자동 abort 경로를 사용한다.
