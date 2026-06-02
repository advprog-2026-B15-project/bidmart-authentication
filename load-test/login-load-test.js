import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend } from 'k6/metrics';

const errorRate = new Rate('error_rate');
const loginDuration = new Trend('login_duration_ms');

export const options = {
  stages: [
    { duration: '30s', target: 5 },
    { duration: '1m',  target: 10 },
    { duration: '30s', target: 0 },
  ],
  thresholds: {
    http_req_duration: ['p(95)<500'],
    error_rate: ['rate<0.1'],
  },
};

const BASE_URL = 'http://localhost:8081';

const TEST_EMAIL = 'test@demo.com';
const TEST_PASSWORD = 'Test1234!';

export default function () {
  const payload = JSON.stringify({ email: TEST_EMAIL, password: TEST_PASSWORD });
  const params  = { headers: { 'Content-Type': 'application/json' } };

  const res = http.post(`${BASE_URL}/api/auth/login`, payload, params);

  loginDuration.add(res.timings.duration);

  const ok = check(res, {
    'status 200': (r) => r.status === 200,
    'has accessToken': (r) => {
      try { return JSON.parse(r.body).accessToken !== null; } catch { return false; }
    },
    'response < 500ms': (r) => r.timings.duration < 500,
  });

  errorRate.add(!ok);
  sleep(1);
}
