-- Historical credits remain unlinked; no inferred purchase or changes to amounts/status.
ALTER TABLE credit_card_refunds ADD COLUMN purchase_id BIGINT;
ALTER TABLE credit_card_refunds ADD CONSTRAINT fk_credit_card_refunds_purchase
    FOREIGN KEY (purchase_id) REFERENCES credit_card_purchases(id) ON DELETE RESTRICT;
CREATE INDEX idx_credit_card_refunds_purchase ON credit_card_refunds(purchase_id);
