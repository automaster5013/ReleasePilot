from pathlib import Path

from .models import AnalysisRequest, AnalysisResponse, MetricEvidence, MetricRule, Verdict
from .prometheus import PrometheusClient, PrometheusError
from .templates import QueryTemplateRegistry, RenderedQuery


class AnalysisEvaluator:
    def __init__(self, registry: QueryTemplateRegistry, prometheus: PrometheusClient):
        self._registry = registry
        self._prometheus = prometheus

    async def evaluate(self, request: AnalysisRequest) -> AnalysisResponse:
        evidence = [await self._evaluate_metric(request, rule) for rule in request.metrics]
        required = [
            item
            for item, rule in zip(evidence, request.metrics, strict=True)
            if rule.required
        ]
        if any(item.verdict == Verdict.FAIL for item in required):
            return AnalysisResponse(
                verdict=Verdict.FAIL,
                reason_code="REQUIRED_RULE_FAILED",
                evidence=evidence,
            )
        if any(item.verdict == Verdict.INCONCLUSIVE for item in required):
            return AnalysisResponse(
                verdict=Verdict.INCONCLUSIVE,
                reason_code="REQUIRED_RULE_INCONCLUSIVE",
                evidence=evidence,
            )
        return AnalysisResponse(
            verdict=Verdict.PASS, reason_code="ALL_RULES_PASSED", evidence=evidence
        )

    async def _evaluate_metric(
        self, request: AnalysisRequest, rule: MetricRule
    ) -> MetricEvidence:
        common = request.labels.model_dump()
        seconds = int((request.window_end - request.window_start).total_seconds())
        common["window"] = f"{seconds}s"
        canary_query = self._registry.render(
            rule.key, {**common, "release_track": "canary"}
        )
        baseline_query = self._registry.render(
            rule.key, {**common, "release_track": "stable"}
        )
        baseline: float | None = None
        try:
            canary = await self._query(request, canary_query)
            if rule.relativeToBaseline is not None:
                baseline = await self._query(request, baseline_query)
            verdict, reason = self._judge(rule, canary, baseline)
        except PrometheusError as error:
            canary = None
            verdict, reason = Verdict.INCONCLUSIVE, error.reason_code
        return MetricEvidence(
            metric_key=rule.key,
            verdict=verdict,
            reason_code=reason,
            baseline_value=baseline,
            canary_value=canary,
            threshold=rule.threshold,
            query_template_id=canary_query.template_id,
            baseline_query_hash=baseline_query.query_hash if rule.relativeToBaseline else None,
            canary_query_hash=canary_query.query_hash,
            window_start=request.window_start,
            window_end=request.window_end,
            source_id=request.source_id,
            policy_snapshot_checksum=request.policy_snapshot_checksum,
        )

    async def _query(self, request: AnalysisRequest, query: RenderedQuery) -> float:
        return await self._prometheus.query(
            str(request.prometheus_url),
            query.query,
            request.window_end.timestamp(),
            request.bearer_token,
        )

    def _judge(
        self, rule: MetricRule, canary: float, baseline: float | None
    ) -> tuple[Verdict, str]:
        if rule.key == "REQUEST_COUNT" and canary < rule.threshold:
            return Verdict.INCONCLUSIVE, "INSUFFICIENT_SAMPLE"
        absolute_pass = (
            canary < rule.threshold
            if rule.comparison == "LESS_THAN"
            else canary >= rule.threshold
        )
        if not absolute_pass:
            return Verdict.FAIL, "THRESHOLD_EXCEEDED"
        relative = rule.relativeToBaseline
        if relative and baseline is not None:
            if (
                relative.maximumAbsoluteIncrease is not None
                and canary - baseline > relative.maximumAbsoluteIncrease
            ):
                return Verdict.FAIL, "BASELINE_REGRESSION"
            if relative.maximumRelativeIncreasePercent is not None:
                increase = float("inf") if baseline <= 0 else (canary - baseline) / baseline * 100
                if increase > relative.maximumRelativeIncreasePercent:
                    return Verdict.FAIL, "BASELINE_REGRESSION"
        return Verdict.PASS, "ALL_RULES_PASSED"


def default_registry() -> QueryTemplateRegistry:
    return QueryTemplateRegistry(Path(__file__).with_name("query-templates-v1.yaml"))
