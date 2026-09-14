# Analysis Worker

Prometheus를 조회하고 정책 규칙별 계산 증거를 반환하는 Python Worker입니다. 최종 승격 또는 롤백 결정은 Control Plane이 수행합니다.

`POST /v1/analyses`는 고정된 query template과 검증된 label만 사용합니다. 응답에는 stable/canary 값, 판정 사유, 시간창, template ID, query hash와 policy snapshot checksum이 포함되며 Bearer token은 포함되지 않습니다.

## 실행

Python 3.13 또는 3.14와 uv 사용을 권장합니다.

```powershell
uv sync
uv run uvicorn releasepilot_analysis_worker.main:app --reload --port 8090
```

## 검사

```powershell
uv run ruff check .
uv run pytest
```
