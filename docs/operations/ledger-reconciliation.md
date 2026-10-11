# Expense Core ledger reconciliation

Run these read-only checks against the Expense Core PostgreSQL database during
release validation and on a scheduled operational cadence. They inspect the
authoritative immutable `balance_postings` stream; do not repair rows directly.
Any non-empty result is a release blocker and requires an audited correction.

## 1. Zero-sum ledger by group and currency

Every group/currency posting stream must net to zero, including compensating
postings created by expense updates, deletion, and settlement reversal.

```sql
SELECT group_id, currency, SUM(amount_minor) AS net_amount_minor,
       COUNT(*) AS posting_count
FROM balance_postings
GROUP BY group_id, currency
HAVING SUM(amount_minor) <> 0;
```

Expected result: zero rows.

## 2. Active expense coverage

Every non-deleted expense must have at least one posting in its group and its
posting currency must match the expense currency.

```sql
SELECT e.expense_id, e.group_id, e.currency
FROM expenses e
LEFT JOIN balance_postings p ON p.expense_id = e.expense_id
WHERE e.deleted = FALSE
GROUP BY e.expense_id, e.group_id, e.currency
HAVING COUNT(p.posting_id) = 0
    OR COUNT(*) FILTER (WHERE p.currency <> e.currency) > 0;
```

Expected result: zero rows.

## 3. Settlement coverage and amount reconciliation

Each settlement must have exactly two original participant postings. A reversed
settlement must additionally have exactly two compensating postings, while a
recorded settlement must not have compensating postings.

```sql
WITH posting_totals AS (
  SELECT settlement_id,
         COUNT(*) AS posting_count,
         SUM(amount_minor) AS net_amount_minor
  FROM balance_postings
  WHERE settlement_id IS NOT NULL
  GROUP BY settlement_id
)
SELECT s.settlement_id, s.status, s.amount_minor,
       COALESCE(p.posting_count, 0) AS posting_count,
       COALESCE(p.net_amount_minor, 0) AS net_amount_minor
FROM settlements s
LEFT JOIN posting_totals p ON p.settlement_id = s.settlement_id
WHERE (s.status = 'RECORDED' AND COALESCE(p.posting_count, 0) <> 2)
   OR (s.status = 'REVERSED' AND COALESCE(p.posting_count, 0) <> 4)
   OR COALESCE(p.net_amount_minor, 0) <> 0;
```

Expected result: zero rows. The posting signs should also match the settlement
direction for the first pair:

```sql
SELECT s.settlement_id, s.from_participant_id, s.to_participant_id,
       from_posting.amount_minor AS from_amount_minor,
       to_posting.amount_minor AS to_amount_minor
FROM settlements s
JOIN balance_postings from_posting
  ON from_posting.settlement_id = s.settlement_id
 AND from_posting.participant_id = s.from_participant_id
 AND from_posting.amount_minor = s.amount_minor
JOIN balance_postings to_posting
  ON to_posting.settlement_id = s.settlement_id
 AND to_posting.participant_id = s.to_participant_id
 AND to_posting.amount_minor = -s.amount_minor
WHERE s.status IN ('RECORDED', 'REVERSED');
```

For an audit report, compare the returned row count with the settlement count;
they must be equal. The foreign keys on `balance_postings.expense_id` and
`balance_postings.settlement_id` independently reject orphan references.

Every settlement posting must also match the settlement's currency:

```sql
SELECT s.settlement_id, s.group_id, s.currency AS settlement_currency,
       p.currency AS posting_currency
FROM settlements s
JOIN balance_postings p ON p.settlement_id = s.settlement_id
WHERE p.currency <> s.currency;
```

Expected result: zero rows. Any mismatch indicates currency pollution between group and settlement streams.

## 4. Release evidence

Record the database identifier, UTC execution time, migration version, query
results, and operator in the release evidence ledger. A successful application
test or local in-memory test is not a substitute for these database checks.
