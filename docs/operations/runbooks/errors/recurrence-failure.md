# Recurrence generation failure

## Summary

Recurring expense occurrence generation failed and requires bounded operator review.

## Severity and blast radius

Critical for a sustained scheduler failure; affected recurring schedules may not produce expected occurrences.

## Immediate triage

Inspect the error code, scheduler lag, database health, and the last successful occurrence timestamp. Do not create duplicate occurrences manually.

## Diagnostics

```text
docker compose logs --since 15m expense-core
sum by (service, code) (rate(squarewise_errors_total{service="expense-core"}[5m]))
```

## Remediation and rollback

Restore dependency health, replay only idempotent scheduler work, and reconcile generated occurrences. Roll back the scheduler deployment only after checking schema compatibility.
