# Notification payload corruption

## Summary

A notification message failed payload validation before dispatch.

## Severity and blast radius

Critical for sustained ingestion; affected notification delivery may be delayed while financial state remains independent.

## Immediate triage

Inspect the message envelope, producer version, dead-letter queue, and consumer validation logs without exposing payload secrets.

## Diagnostics

```text
docker compose logs --since 15m notifications rabbitmq
```

## Remediation and rollback

Quarantine the invalid payload, correct the producer or migration, and replay only validated messages. Roll back the producer if the current release caused the schema mismatch.
