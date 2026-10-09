# CORE-29: Multi-currency settlement ledger persistence, Flyway migration, and replay validation

## Objective

Remediate critical multi-currency settlement defects (`AUD-01`, `AUD-02`, `AUD-10`) identified in the financial audit:
- Implement Flyway schema migration `V11__add_settlement_currency.sql` adding `currency VARCHAR(3) NOT NULL` to the `settlements` table, backfilling existing rows from their parent `expense_groups.currency`.
- Update `SettlementEntity` with `@Column(name = "currency", nullable = false, length = 3) var currency: String`.
- Update `JpaSettlementStore` to persist the settlement's explicit currency into `SettlementEntity` and generate double-entry `BalancePostingEntity` rows using `settlement.currency` rather than `group.currency`.
- Update settlement idempotency conflict validation in `JpaSettlementStore.record` to verify `existing.currency == settlement.currency` and throw `ExpenseDomainException(PlatformErrors.RESOURCE_CONFLICT)` on currency alteration.
- Update `JpaSettlementStore.reverse` to maintain exact settlement currency on reversal postings.
- Update Section 3 of `docs/operations/ledger-reconciliation.md` with SQL verification checking that every settlement posting matches its settlement currency.

## Design Patterns & Architectural Guidance

- **Double-Entry Ledger Pattern**: Every recorded settlement generates exactly two balancing postings of equal and opposite sign netting to zero in the settlement's currency: `+amountMinor` for `fromParticipantId` and `-amountMinor` for `toParticipantId`.
- **Value Object Pattern**: Treat `(currency, amountMinor)` as an inseparable monetary tuple. Never default or substitute the currency from an ambient group container.
- **Transactional Outbox & Optimistic Locking**: Ensure the settlement insertion, ledger postings, group revision increment, and outbox notification occur within the same local PostgreSQL database transaction.
- **Fail-Fast Idempotency Check**: Replay of an identical idempotency key with differing financial dimensions (`from`, `to`, `amountMinor`, or `currency`) must fail immediately with HTTP 409 Conflict (`STATE_CONFLICT`).

## Dependencies

- `CORE-27`
- `DB-06`

## Owned paths

- `app/expense-core/src/main/resources/db/migration/V12__add_settlement_currency.sql`
- `app/expense-core/src/main/kotlin/com/subhrodip/squarewise/expensecore/settlements/`
- `app/expense-core/src/test/kotlin/com/subhrodip/squarewise/expensecore/settlements/`
- `docs/operations/ledger-reconciliation.md`
- `docs/tasks/details/CORE-29.md`

## Acceptance criteria

- `V12__add_settlement_currency.sql` executes successfully against PostgreSQL and H2, adds `currency VARCHAR(3) NOT NULL`, and backfills existing rows without data loss.
- `SettlementEntity` and `JpaSettlementStore` map and persist the caller-specified currency.
- Settlements recorded in a currency different from the group's default currency (e.g. `USD` in a `EUR` group) post ledger entries in `USD`.
- Replaying the same idempotency key with a changed `currency` throws `STATE_CONFLICT` / HTTP 409 Conflict.
- Reversing a multi-currency settlement creates compensating entries in the settlement's currency.
- Unit and SpringBoot integration tests verify multi-currency recording, idempotency conflict, and reversal.

## Validation commands

- `./gradlew :app:expense-core:test --tests "com.subhrodip.squarewise.expensecore.settlements.*" --no-daemon`
- `make contracts`
- `git diff --check`

## Implementation Notes & Evidence

- **AUD-01 & AUD-10 Remediation**: Added Flyway migration `V12__add_settlement_currency.sql` with default and backfill from `expense_groups.currency`. Updated `SettlementEntity` to declare persistent column `currency`. Updated `JpaSettlementStore.record` to generate balance postings using `saved.currency` instead of `group.currency`, and `reverse` to preserve the settlement's original currency.
- **AUD-02 Remediation**: Updated replay idempotency checks in both `JpaSettlementStore.record` and `InMemorySettlementStore.record` to verify `existing.currency == settlement.currency` and throw `GROUP_NAME_CONFLICT` / `RESOURCE_CONFLICT` (HTTP 409) if mutated.
- **Ledger Reconciliation**: Section 3 of `docs/operations/ledger-reconciliation.md` updated with SQL verification query confirming that `balance_postings.currency` matches `settlements.currency`.
- **Evidence**:
  - `./gradlew :app:expense-core:test --tests "com.subhrodip.squarewise.expensecore.settlements.*" --no-daemon` passed all 32 tests.
  - `make contracts` verified valid JSON, valid GraphQL, 257 valid tasks, 45 operations, 99 six-digit error catalog records.
  - `git diff --check` reported zero trailing whitespace or format issues.
