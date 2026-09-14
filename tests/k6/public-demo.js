import http from "k6/http";
import { check } from "k6";

export const options = {
  scenarios: {
    readers: { executor: "constant-vus", vus: 10, duration: "60s" },
  },
  thresholds: {
    http_req_failed: ["rate<0.01"],
    http_req_duration: ["p(95)<750"],
    checks: ["rate>0.99"],
  },
};

const baseUrl = __ENV.BASE_URL || "http://localhost:3000";

export default function () {
  const home = http.get(`${baseUrl}/`);
  check(home, { "console returns 200": (response) => response.status === 200 });

  const csrf = http.get(`${baseUrl}/control-api/session/csrf`);
  check(csrf, { "csrf endpoint returns 200": (response) => response.status === 200 });
  if (csrf.status !== 200) return;

  const token = csrf.json();
  const demo = http.post(`${baseUrl}/control-api/session/demo`, null, {
    headers: { [token.headerName]: token.token },
  });
  check(demo, {
    "demo session returns 200": (response) => response.status === 200,
    "demo is viewer only": (response) => response.json("user.roles.0") === "VIEWER",
  });
}
