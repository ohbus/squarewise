# Upstream timeout (`428701`)

## Summary

The BFF or a service gateway exceeded its bounded upstream timeout.

## Severity

Critical when sustained; transient isolated events are retryable according to the client policy.

## Blast Radius

Dependent GraphQL or REST operations may return contained timeout errors while local financial state remains unchanged.

## Immediate Triage

Compare p95/p99 latency, gateway timeout counts, downstream health, and retry volume. Check that retries are not amplifying the incident.

## Diagnostic Commands

```text
sum by (service) (rate(http_server_requests_seconds_count{status="504"}[5m]))
docker compose logs --since 15m bff accounts expense-core notifications
```

## Remediation Steps

Restore the slow dependency, reduce request pressure, and verify timeout and circuit-breaker bounds before reopening traffic.

## Rollback Procedures

Roll back a newly introduced gateway or timeout configuration only after confirming compatibility with downstream service budgets.
