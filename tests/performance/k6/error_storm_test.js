import http from 'k6/http';
import { check } from 'k6';
import { sleep } from 'k6';

const baseUrl = __ENV.BASE_URL || 'http://localhost:28081';
const token = __ENV.BEARER_TOKEN || '';
const duration = __ENV.DURATION || '10m';

export const options = {
  scenarios: {
    error_storm: {
      executor: 'constant-arrival-rate',
      rate: 2500,
      timeUnit: '1s',
      duration,
      preAllocatedVUs: 500,
      maxVUs: 5000,
      exec: 'errorStorm',
      tags: { workload: 'error-storm' },
    },
    valid_operations: {
      executor: 'constant-arrival-rate',
      rate: 2500,
      timeUnit: '1s',
      duration,
      preAllocatedVUs: 500,
      maxVUs: 5000,
      exec: 'validOperation',
      tags: { workload: 'valid-operation' },
    },
  },
  thresholds: {
    'http_req_duration{workload:error-storm}': ['p(99)<25'],
    'http_req_duration{workload:valid-operation}': ['p(99)<500'],
    'http_req_failed{workload:error-storm}': ['rate<0.01'],
    dropped_iterations: ['count==0'],
  },
};

export function setup() {
  if (!token) {
    throw new Error('BEARER_TOKEN is required; no legacy placeholder fallback is permitted');
  }
  return { token };
}

export function errorStorm() {
  const response = http.get(`${baseUrl}/accounts/v1/profiles/not-a-uuid`, {
    headers: { Accept: 'application/json' },
    tags: { endpoint: 'invalid-profile' },
  });
  check(response, { 'error response is contained': (value) => value.status >= 400 && value.status < 500 });
}

export function validOperation(data) {
  const response = http.get(`${baseUrl}/accounts/v1/me`, {
    headers: { Accept: 'application/json', Authorization: `Bearer ${data.token}` },
    tags: { endpoint: 'accounts-me' },
  });
  check(response, { 'valid operation succeeds': (value) => value.status === 200 });
  sleep(0.001);
}
