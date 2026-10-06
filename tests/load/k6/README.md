# k6 modular load tests

Each script targets one high-value public path and can run independently:

```sh
k6 run tests/load/k6/accounts.js
k6 run tests/load/k6/expense-core.js
k6 run tests/load/k6/notifications.js
k6 run tests/load/k6/bff-graphql.js
k6 run tests/load/k6/rate-limit-graphql.js
```

When k6 is not installed locally, use the pinned workflow entry point supplied
by the repository (the image tag should be pinned by the deployment team before
CI use):

```sh
make load-k6 SCRIPT=tests/load/k6/accounts.js
```

The Docker runner uses host networking for local services. In a CI or remote
environment, set the URL variables to addresses reachable from the k6 runner.

The 1M-user profile defaults to ten minutes; use `DURATION=10s k6 run
tests/load/k6/one-million-baseline.js` for a short wiring smoke test. A short
run is not capacity evidence.

The fixture-backed mutation scenario is separate and creates a disposable group:

```sh
DURATION=10s k6 run tests/load/k6/mutation-expense.js
```

Run it only against an isolated environment. The script archives its fixture
group in k6 teardown, but retain and review the reconciliation output; do not
use this scenario against production data until the OPS-23 gate is approved.

Configure `BASE_URL`, `ACCOUNTS_URL`, `EXPENSE_CORE_URL`,
`NOTIFICATIONS_URL`, and `BEARER_TOKEN` for the target environment. A signed
`BEARER_TOKEN` is required; the load harness has no legacy placeholder fallback.
rates and thresholds are provisional starting points; OPS-20 requires replacing
them with measured capacity and SLO evidence. Run mutation scenarios separately
with unique fixture data and idempotency keys; these read-focused scripts are
safe to repeat.

For an admission-focused limiter probe, run `rate-limit-graphql.js` with a
temporarily raised isolated BFF HTTP limit. It uses `{ __typename }` so the
measurement covers BFF/Redis admission and GraphQL parsing without downstream
Accounts gateway latency. Set `BASE_URLS` to a comma-separated list to
alternate requests across multiple BFF replicas. Local results are
wiring/capacity evidence only.
