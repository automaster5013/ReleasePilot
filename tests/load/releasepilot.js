import http from 'k6/http';
import { check, sleep } from 'k6';

const baseUrl = (__ENV.BASE_URL || 'https://releasepilot.example.com').replace(/\/$/, '');
const sessionCookie = __ENV.RELEASEPILOT_SESSION || '';

export const options = {
  scenarios: {
    steady: {
      executor: 'ramping-vus',
      stages: [
        { duration: '2m', target: 25 },
        { duration: '5m', target: 25 },
        { duration: '2m', target: 75 },
        { duration: '5m', target: 75 },
        { duration: '1m', target: 0 },
      ],
      gracefulRampDown: '30s',
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<1000', 'p(99)<2000'],
    checks: ['rate>0.99'],
  },
};

export default function () {
  const headers = sessionCookie ? { Cookie: `RELEASEPILOT_SESSION=${sessionCookie}` } : {};
  const responses = http.batch([
    ['GET', `${baseUrl}/`, null, { headers }],
    ['GET', `${baseUrl}/actuator/health/readiness`, null, { headers }],
  ]);
  check(responses[0], { 'console responds': (response) => response.status === 200 });
  check(responses[1], { 'control plane is ready': (response) => response.status === 200 });
  sleep(1);
}
