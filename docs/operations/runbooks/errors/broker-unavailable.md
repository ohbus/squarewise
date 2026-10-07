# Broker unavailable

## Summary

RabbitMQ is unavailable or rejecting connections for a service boundary.

## Severity and blast radius

Critical for asynchronous delivery and outbox growth; synchronous financial state must remain transactional.

## Immediate triage

Check broker health, connection failures, queue depth, and outbox age. Avoid increasing retry concurrency.

## Diagnostics

```text
docker compose ps rabbitmq
docker compose logs --since 15m rabbitmq expense-core notifications
```

## Remediation and rollback

Restore broker quorum or connectivity, verify publisher confirms, and drain queues under bounded concurrency. Roll back only broker-client regressions.
