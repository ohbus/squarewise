# Platform configuration failure

## Summary

A service rejected unsafe or incomplete startup configuration.

## Severity and blast radius

Critical; the service must remain unavailable rather than start with unsafe defaults.

## Immediate triage

Inspect configuration validation logs and deployment secret/key references without printing values.

## Diagnostics

```text
docker compose logs --since 15m accounts expense-core notifications bff
```

## Remediation and rollback

Correct the versioned configuration or secret reference, restart the affected service, and verify health plus protected endpoint behavior. Roll back the configuration revision if validation still fails.
