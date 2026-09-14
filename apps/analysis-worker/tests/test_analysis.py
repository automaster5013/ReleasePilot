import asyncio
from datetime import UTC, datetime, timedelta
from pathlib import Path

import pytest
import yaml

from releasepilot_analysis_worker.evaluator import AnalysisEvaluator
from releasepilot_analysis_worker.models import AnalysisRequest, Verdict
from releasepilot_analysis_worker.prometheus import PrometheusError
from releasepilot_analysis_worker.templates import QueryTemplateRegistry


class FakePrometheus:
    def __init__(
        self,
        values: dict[tuple[str, str], float] | None = None,
        error: str | None = None,
    ):
        self.values = values or {}
        self.error = error

    async def query(self, base_url: str, query: str, timestamp: float, token: str | None) -> float:
        if self.error:
            raise PrometheusError(self.error)
        if "duration_seconds_bucket" in query:
            metric = "HTTP_P95_LATENCY_MS"
        elif 'http_response_status_code=~"5.."' in query:
            metric = "HTTP_5XX_RATE"
        else:
            metric = "REQUEST_COUNT"
        track = "canary" if 'release_track="canary"' in query else "stable"
        return self.values[(metric, track)]


def request() -> AnalysisRequest:
    end = datetime.now(UTC)
    return AnalysisRequest.model_validate(
        {
            "prometheus_url": "http://prometheus:9090",
            "source_id": "prom-main",
            "policy_snapshot_checksum": "sha256:test",
            "window_start": end - timedelta(minutes=5),
            "window_end": end,
            "labels": {
                "service_namespace": "shop",
                "service_name": "checkout",
                "deployment_environment": "production",
            },
            "metrics": [
                {"key": "HTTP_5XX_RATE", "comparison": "LESS_THAN", "threshold": 0.01,
                 "relativeToBaseline": {"maximumAbsoluteIncrease": 0.005}},
                {"key": "HTTP_P95_LATENCY_MS", "comparison": "LESS_THAN", "threshold": 500,
                 "relativeToBaseline": {"maximumRelativeIncreasePercent": 20}},
                {"key": "REQUEST_COUNT", "comparison": "GREATER_THAN_OR_EQUAL", "threshold": 1000},
            ],
        }
    )


def registry() -> QueryTemplateRegistry:
    return QueryTemplateRegistry(
        Path(__file__).parents[1] / "src/releasepilot_analysis_worker/query-templates-v1.yaml"
    )


def test_all_metrics_pass():
    values = {
        ("HTTP_5XX_RATE", "canary"): 0.004, ("HTTP_5XX_RATE", "stable"): 0.002,
        ("HTTP_P95_LATENCY_MS", "canary"): 110, ("HTTP_P95_LATENCY_MS", "stable"): 100,
        ("REQUEST_COUNT", "canary"): 1200,
    }
    result = asyncio.run(AnalysisEvaluator(registry(), FakePrometheus(values)).evaluate(request()))
    assert result.verdict == Verdict.PASS
    assert all(len(item.canary_query_hash) == 64 for item in result.evidence)


def test_regression_fails_and_insufficient_sample_is_inconclusive():
    values = {
        ("HTTP_5XX_RATE", "canary"): 0.009, ("HTTP_5XX_RATE", "stable"): 0.001,
        ("HTTP_P95_LATENCY_MS", "canary"): 100, ("HTTP_P95_LATENCY_MS", "stable"): 100,
        ("REQUEST_COUNT", "canary"): 100,
    }
    result = asyncio.run(AnalysisEvaluator(registry(), FakePrometheus(values)).evaluate(request()))
    assert result.verdict == Verdict.FAIL
    assert result.evidence[0].reason_code == "BASELINE_REGRESSION"
    assert result.evidence[2].reason_code == "INSUFFICIENT_SAMPLE"


def test_prometheus_failure_never_promotes():
    result = asyncio.run(
        AnalysisEvaluator(registry(), FakePrometheus(error="QUERY_TIMEOUT")).evaluate(request())
    )
    assert result.verdict == Verdict.INCONCLUSIVE
    assert {item.reason_code for item in result.evidence} == {"QUERY_TIMEOUT"}


def test_template_rejects_label_injection():
    with pytest.raises(ValueError, match="invalid query variable"):
        registry().render("REQUEST_COUNT", {
            "service_namespace": 'shop"} or vector(1)', "service_name": "checkout",
            "deployment_environment": "production", "release_track": "canary", "window": "300s",
        })


def test_packaged_templates_match_repository_contract():
    root = Path(__file__).parents[3]
    canonical = yaml.safe_load(
        (root / "config/observability/query-templates-v1.yaml").read_text(encoding="utf-8")
    )
    packaged = yaml.safe_load(
        (Path(__file__).parents[1] / "src/releasepilot_analysis_worker/query-templates-v1.yaml")
        .read_text(encoding="utf-8")
    )
    assert packaged["name"] == canonical["name"]
    assert packaged["allowedVariables"] == canonical["allowedVariables"]
    assert {key: value["promql"] for key, value in packaged["metrics"].items()} == {
        key: " ".join(value["promql"].split()) for key, value in canonical["metrics"].items()
    }
