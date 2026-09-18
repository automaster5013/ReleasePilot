import math
import os
from urllib.parse import urlsplit

import httpx


class PrometheusError(RuntimeError):
    def __init__(self, reason_code: str):
        super().__init__(reason_code)
        self.reason_code = reason_code


class PrometheusClient:
    def __init__(self, timeout_seconds: float = 8.0):
        self._timeout = timeout_seconds

    async def query(self, base_url: str, query: str, timestamp: float, token: str | None) -> float:
        parsed = urlsplit(base_url)
        allowed_hosts = {
            host.strip().encode("idna").decode("ascii").lower()
            for host in os.getenv("PROMETHEUS_ALLOWED_HOSTS", "").split(",")
            if host.strip()
        }
        host = (parsed.hostname or "").encode("idna").decode("ascii").lower()
        if parsed.scheme not in {"http", "https"} or parsed.username or parsed.password:
            raise PrometheusError("TARGET_NOT_ALLOWED")
        if not host or host not in allowed_hosts:
            raise PrometheusError("TARGET_NOT_ALLOWED")
        headers = {"Authorization": f"Bearer {token}"} if token else {}
        try:
            async with httpx.AsyncClient(timeout=self._timeout) as client:
                response = await client.get(
                    f"{base_url.rstrip('/')}/api/v1/query",
                    params={"query": query, "time": timestamp},
                    headers=headers,
                )
                response.raise_for_status()
                body = response.json()
        except httpx.TimeoutException as error:
            raise PrometheusError("QUERY_TIMEOUT") from error
        except (httpx.HTTPError, ValueError) as error:
            raise PrometheusError("PROMETHEUS_UNAVAILABLE") from error
        result = body.get("data", {}).get("result", [])
        if body.get("status") != "success" or len(result) != 1:
            raise PrometheusError("MISSING_SERIES")
        try:
            value = float(result[0]["value"][1])
        except (KeyError, IndexError, TypeError, ValueError) as error:
            raise PrometheusError("INVALID_RESULT") from error
        if not math.isfinite(value):
            raise PrometheusError("INVALID_RESULT")
        return value
