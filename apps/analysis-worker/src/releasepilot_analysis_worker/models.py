from datetime import datetime
from enum import StrEnum

from pydantic import BaseModel, Field, HttpUrl, model_validator


class Verdict(StrEnum):
    PASS = "PASS"
    FAIL = "FAIL"
    INCONCLUSIVE = "INCONCLUSIVE"


class RouteImportance(StrEnum):
    STANDARD = "STANDARD"
    CRITICAL = "CRITICAL"


class RelativeLimit(BaseModel):
    maximumAbsoluteIncrease: float | None = None
    maximumRelativeIncreasePercent: float | None = None


class MetricRule(BaseModel):
    key: str
    required: bool = True
    comparison: str
    threshold: float
    relativeToBaseline: RelativeLimit | None = None
    route: str | None = Field(default=None,pattern=r"^/[-A-Za-z0-9._~/{}]+$")
    importance: RouteImportance = RouteImportance.STANDARD

    @model_validator(mode="after")
    def validate_route_importance(self) -> "MetricRule":
        if self.importance == RouteImportance.CRITICAL and self.route is None:
            raise ValueError("CRITICAL importance requires route")
        return self


class Labels(BaseModel):
    service_namespace: str = Field(pattern=r"^[a-z0-9]([-a-z0-9]*[a-z0-9])?$")
    service_name: str = Field(pattern=r"^[a-z0-9]([-a-z0-9]*[a-z0-9])?$")
    deployment_environment: str = Field(pattern=r"^(staging|production)$")


class AnalysisRequest(BaseModel):
    prometheus_url: HttpUrl
    bearer_token: str | None = None
    source_id: str
    policy_snapshot_checksum: str
    window_start: datetime
    window_end: datetime
    labels: Labels
    metrics: list[MetricRule]

    @model_validator(mode="after")
    def validate_window(self) -> "AnalysisRequest":
        if self.window_end <= self.window_start:
            raise ValueError("window_end must be after window_start")
        if not self.metrics:
            raise ValueError("at least one metric is required")
        return self


class MetricEvidence(BaseModel):
    metric_key: str
    verdict: Verdict
    reason_code: str
    baseline_value: float | None = None
    canary_value: float | None = None
    threshold: float
    query_template_id: str
    baseline_query_hash: str | None = None
    canary_query_hash: str
    window_start: datetime
    window_end: datetime
    source_id: str
    policy_snapshot_checksum: str
    route: str | None = None
    importance: RouteImportance = RouteImportance.STANDARD


class AnalysisResponse(BaseModel):
    verdict: Verdict
    reason_code: str
    evidence: list[MetricEvidence]
