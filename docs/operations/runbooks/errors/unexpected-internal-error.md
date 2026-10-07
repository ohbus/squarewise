# Unexpected internal error

## Summary

A governed internal failure escaped normal domain classification and was safely contained at the transport boundary.

## Severity and blast radius

Critical until the affected operation and dependency are understood.

## Immediate triage

Use the request ID and bounded service metrics. Do not expose or copy raw exception messages into tickets or client responses.

## Diagnostics

```text
docker compose logs --since 15m accounts expense-core notifications bff
sum by (service, code) (rate(squarewise_errors_total{code="919901"}[5m]))
```

## Remediation and rollback

Identify the first failing dependency, apply the smallest safe mitigation, and roll back the responsible artifact when the regression is isolated. Re-run leakage checks after remediation.
