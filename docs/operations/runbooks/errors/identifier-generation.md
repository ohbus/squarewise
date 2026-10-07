# Identifier generation failure

## Summary

A governed identifier source failed and the operation was prevented from emitting an invalid identifier.

## Severity and blast radius

Critical for affected writes; do not retry blindly if idempotency state is uncertain.

## Immediate triage

Check UUID source health, request correlation, and whether the enclosing transaction committed.

## Diagnostics

```text
docker compose logs --since 15m accounts expense-core notifications bff
sum by (service, code) (rate(squarewise_errors_total{code="968901"}[5m]))
```

## Remediation and rollback

Restore the identifier source, verify transaction and idempotency state, and replay only safe requests. Roll back the source change if generation remains unavailable.
