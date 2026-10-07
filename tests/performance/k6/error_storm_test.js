import http from 'k6/http';
import { check } from 'k6';
import { Gauge, Trend } from 'k6/metrics';
import { sleep } from 'k6';

const baseUrl = __ENV.BASE_URL || 'http://localhost:28081';
const token = __ENV.BEARER_TOKEN || '';
const duration = __ENV.DURATION || '10m';
const heapUsedBytes = new Gauge('jvm_heap_used_bytes');
const cpuUsageRatio = new Trend('jvm_process_cpu_usage_ratio');

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
    telemetry: {
      executor: 'constant-arrival-rate',
      rate: 1,
      timeUnit: '1s',
      duration,
      preAllocatedVUs: 1,
      maxVUs: 2,
      exec: 'sampleTelemetry',
      tags: { workload: 'telemetry' },
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

export function sampleTelemetry() {
  const response = http.get(`${baseUrl}/actuator/prometheus`, {
    headers: { Accept: 'text/plain' },
    tags: { endpoint: 'actuator-prometheus' },
  });
  check(response, { 'telemetry endpoint is available': (value) => value.status === 200 });
  if (response.status !== 200) return;
  const heap = metricValue(response.body, 'jvm_memory_used_bytes', '{area="heap"}');
  const cpu = metricValue(response.body, 'process_cpu_usage', '');
  if (heap !== null) heapUsedBytes.add(heap);
  if (cpu !== null) cpuUsageRatio.add(cpu);
}

function metricValue(payload, metricName, labelFragment) {
  const line = payload.split('\n').find((candidate) => candidate.startsWith(metricName + '{') && candidate.includes(labelFragment));
  if (!line) return null;
  const value = Number(line.substring(line.lastIndexOf(' ') + 1));
  return Number.isFinite(value) ? value : null;
}
