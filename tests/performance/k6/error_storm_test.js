import http from 'k6/http';
import { check } from 'k6';
import { Gauge, Trend } from 'k6/metrics';
import { sleep } from 'k6';

const baseUrl = __ENV.BASE_URL || 'http://localhost:28081';
const token = __ENV.BEARER_TOKEN || '';
const duration = __ENV.DURATION || '10m';
const loadMode = __ENV.LOAD_MODE || 'storm';
const errorRate = Number(__ENV.ERROR_RATE || '2500');
const validRate = Number(__ENV.VALID_RATE || '2500');
const baselineValidP99Ms = Number(__ENV.BASELINE_VALID_P99_MS || '0');
const validP99Threshold = baselineValidP99Ms > 0 ? baselineValidP99Ms * 1.05 : 500;
const heapUsedBytes = new Gauge('jvm_heap_used_bytes');
const cpuUsageRatio = new Trend('jvm_process_cpu_usage_ratio');
const gcPauseSecondsTotal = new Trend('jvm_gc_pause_seconds_total');
const gcCollectionsTotal = new Trend('jvm_gc_collections_total');
const containedErrorResponses = http.expectedStatuses(400, 401, 403, 404, 409, 422, 429);

const commonValidScenario = {
  executor: 'constant-arrival-rate',
  rate: validRate,
  timeUnit: '1s',
  duration,
  preAllocatedVUs: 500,
  maxVUs: 5000,
  exec: 'validOperation',
  tags: { workload: 'valid-operation' },
};

const telemetryScenario = {
  executor: 'constant-arrival-rate',
  rate: 1,
  timeUnit: '1s',
  duration,
  preAllocatedVUs: 1,
  maxVUs: 2,
  exec: 'sampleTelemetry',
  tags: { workload: 'telemetry' },
};

const scenarios = {
  valid_operations: commonValidScenario,
  telemetry: telemetryScenario,
};

const thresholds = {
  'http_req_duration{workload:valid-operation}': [`p(99)<${validP99Threshold}`],
  dropped_iterations: ['count==0'],
};

if (loadMode !== 'baseline') {
  scenarios.error_storm = {
    executor: 'constant-arrival-rate',
    rate: errorRate,
    timeUnit: '1s',
    duration,
    preAllocatedVUs: 500,
    maxVUs: 5000,
    exec: 'errorStorm',
    tags: { workload: 'error-storm' },
  };
  thresholds['http_req_duration{workload:error-storm}'] = ['p(99)<25'];
  thresholds['http_req_failed{workload:error-storm}'] = ['rate<0.01'];
}

export const options = {
  scenarios,
  thresholds,
  summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(90)', 'p(95)', 'p(99)'],
};

export function setup() {
  if (!token) {
    throw new Error('BEARER_TOKEN is required; no legacy placeholder fallback is permitted');
  }
  if (loadMode !== 'baseline' && loadMode !== 'storm') {
    throw new Error(`LOAD_MODE must be baseline or storm, received: ${loadMode}`);
  }
  if (!Number.isFinite(errorRate) || errorRate <= 0 || !Number.isFinite(validRate) || validRate <= 0) {
    throw new Error('ERROR_RATE and VALID_RATE must be positive finite numbers');
  }
  if (loadMode === 'storm' && (!Number.isFinite(baselineValidP99Ms) || baselineValidP99Ms <= 0)) {
    throw new Error('BASELINE_VALID_P99_MS must be a positive finite baseline for storm mode');
  }
  // Keep the credential in process-scoped environment state. Returning it from
  // setup would persist the bearer token in --summary-export JSON.
  return {};
}

export function errorStorm() {
  const response = http.get(`${baseUrl}/accounts/v1/profiles/not-a-uuid`, {
    headers: { Accept: 'application/json' },
    responseCallback: containedErrorResponses,
    tags: { endpoint: 'invalid-profile' },
  });
  check(response, { 'error response is contained': (value) => value.status >= 400 && value.status < 500 });
}

export function validOperation() {
  const response = http.get(`${baseUrl}/accounts/v1/me`, {
    headers: { Accept: 'application/json', Authorization: `Bearer ${token}` },
    tags: { endpoint: 'accounts-me' },
  });
  check(response, { 'valid operation succeeds': (value) => value.status === 200 });
  sleep(0.001);
}

export function sampleTelemetry() {
  const response = http.get(`${baseUrl}/actuator/prometheus`, {
    headers: { Accept: 'text/plain', Authorization: `Bearer ${token}` },
    tags: { endpoint: 'actuator-prometheus' },
  });
  check(response, { 'telemetry endpoint is available': (value) => value.status === 200 });
  if (response.status !== 200) return;
  const heap = metricValue(response.body, 'jvm_memory_used_bytes', '{area="heap"}');
  const cpu = metricValue(response.body, 'process_cpu_usage', '');
  const gcPauseSeconds = metricSum(response.body, 'jvm_gc_pause_seconds_sum');
  const gcCollections = metricSum(response.body, 'jvm_gc_pause_seconds_count');
  if (heap !== null) heapUsedBytes.add(heap);
  if (cpu !== null) cpuUsageRatio.add(cpu);
  if (gcPauseSeconds !== null) gcPauseSecondsTotal.add(gcPauseSeconds);
  if (gcCollections !== null) gcCollectionsTotal.add(gcCollections);
}

function metricValue(payload, metricName, labelFragment) {
  const line = payload.split('\n').find((candidate) => candidate.startsWith(metricName + '{') && candidate.includes(labelFragment));
  if (!line) return null;
  const value = Number(line.substring(line.lastIndexOf(' ') + 1));
  return Number.isFinite(value) ? value : null;
}

function metricSum(payload, metricName) {
  const values = payload.split('\n')
    .filter((candidate) => candidate.startsWith(metricName + '{') || candidate === metricName)
    .map((candidate) => Number(candidate.substring(candidate.lastIndexOf(' ') + 1)))
    .filter((value) => Number.isFinite(value));
  return values.length === 0 ? null : values.reduce((sum, value) => sum + value, 0);
}
