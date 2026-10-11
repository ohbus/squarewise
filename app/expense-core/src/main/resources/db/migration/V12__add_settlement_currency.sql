SELECT 1 / CASE WHEN EXISTS (
    SELECT 1
    FROM settlements s
    LEFT JOIN expense_groups g ON g.group_id = s.group_id
    WHERE g.group_id IS NULL
) THEN 0 ELSE 1 END;

SELECT 1 / CASE WHEN EXISTS (
    SELECT 1
    FROM settlements s
    JOIN expense_groups g ON g.group_id = s.group_id
    JOIN balance_postings bp ON bp.settlement_id = s.settlement_id
    WHERE bp.group_id <> s.group_id
       OR bp.currency <> g.currency
) THEN 0 ELSE 1 END;

ALTER TABLE settlements ADD COLUMN currency VARCHAR(3) NOT NULL DEFAULT 'EUR';

UPDATE settlements
SET currency = (
    SELECT g.currency
    FROM expense_groups g
    WHERE g.group_id = settlements.group_id
)
WHERE EXISTS (
    SELECT 1
    FROM expense_groups g
    WHERE g.group_id = settlements.group_id
);

ALTER TABLE settlements ALTER COLUMN currency DROP DEFAULT;
