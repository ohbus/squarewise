# Email dispatch failed (`345701`)

## Summary

Notifications could not dispatch an email through the configured provider.

## Severity

Critical when the outbox or dead-letter queue grows; otherwise error with bounded retry.

## Blast Radius

Magic-link delivery and notification email flows may be delayed. Financial state must remain committed independently of delivery.

## Immediate Triage

Check `code="345701"`, outbox age, broker queue depth, provider connectivity, and dead-letter ingestion.

## Diagnostic Commands

```text
sum by (service, code) (rate(squarewise_errors_total{code="345701"}[5m]))
docker compose logs --since 15m notifications rabbitmq mailpit
```

## Remediation Steps

Restore provider or broker reachability, verify retry bounds, and replay only quarantined messages after confirming idempotency.

## Rollback Procedures

Roll back the notification deployment only if it introduced the failure; preserve outbox records and verify no duplicate delivery.
