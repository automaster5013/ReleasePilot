"""Isolated HTTP workload; metrics describe real handled checkout requests."""
import json
import os
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

BOUNDS = (0.005, 0.01, 0.025, 0.05, 0.1, 0.25, 0.5, 1, 2.5, 5)
lock = threading.Lock()
counts = {200: 0, 500: 0}
buckets = {status: [0] * len(BOUNDS) for status in counts}
sums = {status: 0.0 for status in counts}


def metrics():
    labels = dict(service_namespace=os.getenv("SERVICE_NAMESPACE", "releasepilot-e2e"),
                  service_name="sample-checkout", deployment_environment="staging",
                  release_track=os.getenv("RELEASE_TRACK", "stable"), http_route="/checkout/{id}")
    name = "http_server_request_duration_seconds"
    lines = [f"# TYPE {name} histogram"]
    with lock:
        for status in counts:
            base = labels | {"http_response_status_code": str(status)}
            def tags(extra=None):
                return ",".join(f"{k}={json.dumps(v)}" for k, v in (base | (extra or {})).items())
            for bound, count in zip(BOUNDS, buckets[status]):
                lines.append(f'{name}_bucket{{{tags({"le": str(bound)})}}} {count}')
            lines.append(f'{name}_bucket{{{tags({"le": "+Inf"})}}} {counts[status]}')
            lines.append(f"{name}_count{{{tags()}}} {counts[status]}")
            lines.append(f"{name}_sum{{{tags()}}} {sums[status]}")
    return "\n".join(lines) + "\n"


class Handler(BaseHTTPRequestHandler):
    def do_GET(self):
        path = self.path.split("?", 1)[0]
        if path == "/metrics":
            return self.respond(200, metrics(), "text/plain; version=0.0.4")
        if path == "/health":
            return self.respond(200, '{"status":"UP"}')
        if not path.startswith("/checkout/") or not path[len("/checkout/"):]:
            return self.respond(404, '{"error":"not found"}')
        start = time.perf_counter()
        time.sleep(max(0, min(5, float(os.getenv("CHECKOUT_DELAY_SECONDS", "0")))))
        status = 500 if os.getenv("CHECKOUT_FAIL", "false").lower() == "true" else 200
        elapsed = time.perf_counter() - start
        with lock:
            counts[status] += 1
            sums[status] += elapsed
            for index, bound in enumerate(BOUNDS):
                if elapsed <= bound:
                    buckets[status][index] += 1
        self.respond(status, json.dumps({"version": os.getenv("APP_VERSION", "v1"), "status": status}))

    def respond(self, status, body, content_type="application/json"):
        payload = body.encode()
        self.send_response(status)
        self.send_header("Content-Type", content_type)
        self.send_header("Content-Length", str(len(payload)))
        self.end_headers()
        self.wfile.write(payload)

    def log_message(self, *args):
        pass


if __name__ == "__main__":
    ThreadingHTTPServer((os.getenv("HOST", "127.0.0.1"), int(os.getenv("PORT", "8085"))), Handler).serve_forever()
