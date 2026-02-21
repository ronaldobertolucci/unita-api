CREATE TABLE recurring_transactions
(
    id             BIGSERIAL PRIMARY KEY,
    pocket_id      BIGINT         NOT NULL,
    description    VARCHAR(255)   NOT NULL,
    amount         NUMERIC(15, 2) NOT NULL,
    direction      VARCHAR(10)    NOT NULL CHECK (direction IN ('INCOME', 'EXPENSE')),
    periodicity_id BIGINT         NOT NULL,
    start_date     DATE           NOT NULL,
    end_date       DATE,
    CONSTRAINT fk_recurring_transactions_pocket FOREIGN KEY (pocket_id) REFERENCES pockets (id) ON DELETE CASCADE,
    CONSTRAINT fk_recurring_transactions_periodicity FOREIGN KEY (periodicity_id) REFERENCES recurrence_periodicities (id)
);

CREATE TABLE recurring_purchases
(
    id             BIGSERIAL PRIMARY KEY,
    credit_card_id BIGINT         NOT NULL,
    description    VARCHAR(255)   NOT NULL,
    amount         NUMERIC(15, 2) NOT NULL,
    periodicity_id BIGINT         NOT NULL,
    start_date     DATE           NOT NULL,
    end_date       DATE,
    CONSTRAINT fk_recurring_purchases_credit_card FOREIGN KEY (credit_card_id) REFERENCES credit_cards (id) ON DELETE CASCADE,
    CONSTRAINT fk_recurring_purchases_periodicity FOREIGN KEY (periodicity_id) REFERENCES recurrence_periodicities (id)
);