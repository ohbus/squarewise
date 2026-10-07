# Auth token revoked (`137402`)

## Summary

An authenticated session was rejected because its session or token was revoked.

## Severity

Warning per request; critical only if revocation failures or a broad unexpected spike occur.

## Blast Radius

Only revoked sessions should be affected. A broad increase may indicate cache, key, or session-store failure.

## Immediate Triage

Check `code="137402"` by service and compare with authentication, Redis, and key-rotation signals. Do not ask users to resend credentials before confirming scope.

## Diagnostic Commands

```text
sum by (service, code) (rate(squarewise_errors_total{code="137402"}[5m]))
docker compose logs --since 15m accounts bff redis
```

## Remediation Steps

Confirm revocation state, refresh the client session through the normal flow, and rotate credentials only if compromise indicators exist.

## Rollback Procedures

Do not roll back a revocation without security approval. Roll back only a deployment that introduced false revocations.
