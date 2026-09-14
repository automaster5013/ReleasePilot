import time

from fastapi import FastAPI, Request, Response
from prometheus_client import CONTENT_TYPE_LATEST, Counter, Histogram, generate_latest

from .evaluator import AnalysisEvaluator, default_registry
from .models import AnalysisRequest, AnalysisResponse
from .prometheus import PrometheusClient

app = FastAPI(
    title="ReleasePilot Analysis Worker",
    version="0.1.0",
    docs_url=None,
    redoc_url=None,
)

REQUESTS = Counter(
    "releasepilot_analysis_http_requests_total",
    "Analysis Worker HTTP requests",
    ["method", "path", "status"],
)
DURATION = Histogram(
    "releasepilot_analysis_http_request_duration_seconds",
    "Analysis Worker HTTP latency",
    ["method", "path"],
)


@app.middleware("http")
async def observe_requests(request: Request, call_next):
    started = time.monotonic()
    response = await call_next(request)
    known_paths = {"/health", "/metrics", "/v1/analyses"}
    path = request.url.path if request.url.path in known_paths else "other"
    REQUESTS.labels(request.method, path, response.status_code).inc()
    DURATION.labels(request.method, path).observe(time.monotonic() - started)
    return response


@app.get("/health", tags=["system"])
def health() -> dict[str, str]:
    return {"status": "UP"}


@app.get("/metrics", include_in_schema=False)
def metrics() -> Response:
    return Response(generate_latest(), media_type=CONTENT_TYPE_LATEST)


@app.post("/v1/analyses", response_model=AnalysisResponse, tags=["analysis"])
async def analyze(request: AnalysisRequest) -> AnalysisResponse:
    return await AnalysisEvaluator(default_registry(), PrometheusClient()).evaluate(request)
