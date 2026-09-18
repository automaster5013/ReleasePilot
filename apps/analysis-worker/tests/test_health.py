from fastapi.testclient import TestClient
from test_analysis import request as analysis_request

from releasepilot_analysis_worker.main import app
from releasepilot_analysis_worker.models import AnalysisRequest


def test_health() -> None:
    response = TestClient(app).get("/health")

    assert response.status_code == 200
    assert response.json() == {"status": "UP"}


def test_analysis_requires_service_credential(monkeypatch) -> None:
    monkeypatch.setenv("ANALYSIS_WORKER_SHARED_TOKEN", "worker-test-token")
    payload: AnalysisRequest = analysis_request()
    response = TestClient(app).post("/v1/analyses", json=payload.model_dump(mode="json"))

    assert response.status_code == 401


def test_analysis_rejects_wrong_service_credential(monkeypatch) -> None:
    monkeypatch.setenv("ANALYSIS_WORKER_SHARED_TOKEN", "worker-test-token")
    payload: AnalysisRequest = analysis_request()
    response = TestClient(app).post(
        "/v1/analyses",
        json=payload.model_dump(mode="json"),
        headers={"X-ReleasePilot-Worker-Token": "wrong-token"},
    )

    assert response.status_code == 401
