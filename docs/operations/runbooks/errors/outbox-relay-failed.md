# Outbox relay failure

## Summary

Transactional outbox messages could not be published to the broker.

## Severity and blast radius

Critical when the outbox age or dead-letter queue grows; committed financial state remains authoritative.

## Immediate triage

Check broker reachability, outbox age, publish failures, and consumer backlog. Stop unbounded replay.

## Diagnostics

```text
docker compose logs --since 15m expense-core rabbitmq
sum by (service, code) (rate(squarewise_errors_total{code="285701"}[5m]))
```

## Remediation and rollback

Restore broker health, replay through the idempotent relay, and reconcile outbox state. Roll back only a deployment that introduced the failure.
