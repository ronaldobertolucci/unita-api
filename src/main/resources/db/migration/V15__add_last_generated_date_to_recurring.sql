ALTER TABLE recurring_transactions
    ADD COLUMN last_generated_date DATE;
ALTER TABLE recurring_purchases
    ADD COLUMN last_generated_date DATE;