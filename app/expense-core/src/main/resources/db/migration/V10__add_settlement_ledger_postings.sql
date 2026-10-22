ALTER TABLE balance_postings
    ADD COLUMN settlement_id UUID REFERENCES settlements (settlement_id) ON DELETE CASCADE;

CREATE INDEX balance_postings_settlement_idx
    ON balance_postings (settlement_id);
