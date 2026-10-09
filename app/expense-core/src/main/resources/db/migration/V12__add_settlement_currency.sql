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
