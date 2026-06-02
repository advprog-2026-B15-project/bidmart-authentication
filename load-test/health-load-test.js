import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate } from 'k6/metrics';

const errorRate = new Rate('error_rate');

// Test ringan: health check endpoint untuk ukur baseline throughput
export const options = {
  stages: [
    { duration: '15s', target: 20 },
    { duration: '30s', target: 50 },
    { duration: '15s', target: 0 },
  ],
  thresholds: {
    http_req_duration: ['p(95)<200'],
    error_rate: ['rate<0.01'],
  },
};

const BASE_URL = 'http://localhost:8081';

export default function () {
  const res = http.get(`${BASE_URL}/actuator/health`);

  const ok = check(res, {
    'status 200': (r) => r.status === 200,
    'db UP': (r) => {
      try { return JSON.parse(r.body).components.db.status === 'UP'; } catch { return false; }
    },
  });

  errorRate.add(!ok);
  sleep(0.5);
}
