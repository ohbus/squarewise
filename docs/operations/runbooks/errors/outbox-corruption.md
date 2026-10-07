# Outbox record corruption

## Summary

A transactional outbox record failed structural validation.

## Severity and blast radius

Critical; the affected message must be quarantined without dropping unrelated records.

## Immediate triage

Capture the record identifier and request correlation, inspect the dead-letter path, and preserve the original record for forensic review.

## Diagnostics

```text
docker compose logs --since 15m expense-core rabbitmq
```

## Remediation and rollback

Quarantine the poison record, repair the producing code or data under change control, and replay only after schema validation. Never delete the ledger transaction to clear the queue.
