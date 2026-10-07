# Flyway migration failed (`938101`)

## Summary

This runbook covers startup or deployment failure caused by an unapplied or failed Flyway migration. The requested code is retained as the operational name; verify the active catalog code before paging.

## Severity

Critical; do not allow a partially migrated writer into service.

## Blast Radius

The affected deployment may fail startup or be incompatible with the database schema.

## Immediate Triage

Inspect startup logs, Flyway history, migration checksum, and the deployed immutable artifact digest.

## Diagnostic Commands

```text
docker compose logs --since 15m accounts expense-core notifications
docker compose exec postgres psql -U squarewise -d squarewise -c 'select installed_rank, version, success from flyway_schema_history order by installed_rank desc limit 10;'
```

## Remediation Steps

Stop rollout, restore the last known compatible artifact, and repair only through the approved migration procedure. Never edit migration history by hand without an incident record.

## Rollback Procedures

Use the deployment rollback and forward-fix migration policy; restore a database backup only under the data-recovery runbook.
