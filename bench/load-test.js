// k6 load test for the Hermes ingestion API.
//
//   k6 run bench/load-test.js
//   k6 run -e SCENARIO=soak -e RECIPIENTS=200 bench/load-test.js
//
// Without a local k6 install, run it from the official image:
//
//   docker run --rm -i -v "$PWD/bench:/bench" \
//     -e BASE_URL=http://host.docker.internal:8080 -e SCENARIO=smoke \
//     grafana/k6 run /bench/load-test.js
//
// Scope: this measures the ingestion side only (POST /message -> Postgres). The delivery
// side is asynchronous — the enqueuer polls every 30s and the mailer consumes off the
// broker — so throughput there is measured against the database, not from k6. See
// bench/bench.sh and the "Capacidade" section of the README.
//
// Env vars:
//   BASE_URL     default http://localhost:8080
//   SCENARIO     smoke | ramp (default) | soak | burst
//   RECIPIENTS   recipients per message, default 100
import http from 'k6/http';
import { check } from 'k6';
import { Counter, Trend } from 'k6/metrics';
import { randomString } from 'https://jslib.k6.io/k6-utils/1.4.0/index.js';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const RECIPIENTS = parseInt(__ENV.RECIPIENTS || '100', 10);
const SCENARIO = __ENV.SCENARIO || 'ramp';

// Recipients accepted by the API, so downstream volume can be derived from the k6 output.
export const recipientsAccepted = new Counter('recipients_accepted');
export const recipientsPerSecond = new Trend('recipients_per_request');

const scenarios = {
  // Sanity check that the stack is up before spending time on a real run.
  smoke: {
    executor: 'constant-vus',
    vus: 1,
    duration: '30s',
  },
  // Find the knee: climb until the API starts degrading.
  ramp: {
    executor: 'ramping-vus',
    startVUs: 1,
    stages: [
      { duration: '30s', target: 5 },
      { duration: '1m', target: 10 },
      { duration: '1m', target: 20 },
      { duration: '30s', target: 0 },
    ],
  },
  // Hold a steady rate to see whether latency drifts as the backlog grows.
  soak: {
    executor: 'constant-arrival-rate',
    rate: 10,
    timeUnit: '1s',
    duration: '10m',
    preAllocatedVUs: 20,
    maxVUs: 50,
  },
  // Dump a large backlog as fast as possible. This is the shape that exposes the
  // enqueuer's emitter backpressure ceiling — see the README.
  burst: {
    executor: 'shared-iterations',
    vus: 20,
    iterations: 200,
    maxDuration: '5m',
  },
};

export const options = {
  scenarios: { [SCENARIO]: scenarios[SCENARIO] },
  thresholds: {
    // The API runs with quarkus.transaction-manager.default-transaction-timeout=2s, so a
    // request crossing 2s means the insert transaction is at risk of being rolled back.
    'http_req_duration{expected_response:true}': ['p(95)<2000'],
    http_req_failed: ['rate<0.01'],
  },
};

function buildPayload(runId) {
  const tag = `${runId}-${__VU}-${__ITER}`;
  const recipients = [];
  for (let i = 0; i < RECIPIENTS; i++) {
    recipients.push(`k6-${tag}-${i}@hermes.test`);
  }
  return JSON.stringify({
    title: `k6 ${tag}`,
    text: '<p>load test</p>',
    contentType: 'text/html',
    recipients,
  });
}

export function setup() {
  const res = http.get(`${BASE_URL}/message`);
  if (res.status !== 200) {
    throw new Error(`API not ready at ${BASE_URL}: HTTP ${res.status}`);
  }
  return { runId: randomString(6) };
}

export default function (data) {
  const res = http.post(`${BASE_URL}/message`, buildPayload(data.runId), {
    headers: { 'Content-Type': 'application/json' },
    tags: { name: 'POST /message' },
  });

  const ok = check(res, {
    'status is 200': (r) => r.status === 200,
    'body has id': (r) => {
      try {
        return typeof r.json('id') === 'number';
      } catch (_) {
        return false;
      }
    },
  });

  if (ok) {
    recipientsAccepted.add(RECIPIENTS);
    recipientsPerSecond.add(RECIPIENTS);
  }
}

export function handleSummary(summary) {
  const accepted = summary.metrics.recipients_accepted
    ? summary.metrics.recipients_accepted.values.count
    : 0;
  const seconds = summary.state.testRunDurationMs / 1000;

  const lines = [
    '',
    '=== Hermes ingestion ===',
    `scenario:              ${SCENARIO}`,
    `recipients/message:    ${RECIPIENTS}`,
    `recipients accepted:   ${accepted}`,
    `ingestion rate:        ${(accepted / seconds).toFixed(1)} recipients/s`,
    '',
    'Delivery is asynchronous. To measure the drain, watch the database while this runs:',
    "  watch -n2 \"docker compose exec -T hermes-db psql -U hermes -d hermes -tAc \\\"select recipient_sent, count(*) from hermes.recipient group by 1\\\"\"",
    '',
  ].join('\n');

  // Only stdout: writing a file here breaks when k6 runs inside a container with a
  // different working directory. Use `k6 run --summary-export=file.json` for machine output.
  return { stdout: lines };
}
