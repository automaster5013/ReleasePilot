from fastapi.testclient import TestClient

from releasepilot_analysis_worker.main import app


def test_health() -> None:
    response = TestClient(app).get("/health")

    assert response.status_code == 200
    assert response.json() == {"status": "UP"}
