# Group not found (`213201`)

## Summary

An Expense Core group lookup returned the governed missing-resource response.

## Severity

Warning; treat repeated spikes as a client or synchronization issue.

## Blast Radius

The affected group read or downstream BFF operation is unavailable. Anti-enumeration may intentionally return the same response for unauthorized access.

## Immediate Triage

Check the dashboard by `code="213201"`, request ID, service, and route. Confirm whether the caller has group membership and whether the group was archived.

## Diagnostic Commands

```text
sum by (service, code) (rate(squarewise_errors_total{code="213201"}[5m]))
docker compose logs --since 15m expense-core bff
```

## Remediation Steps

Verify the caller's membership and synchronization watermark. Restore the group only through the approved lifecycle operation; never bypass authorization or expose existence through a different error.

## Rollback Procedures

Roll back only a deployment proven to have introduced the spike, then repeat the authorized read and reconciliation checks.
