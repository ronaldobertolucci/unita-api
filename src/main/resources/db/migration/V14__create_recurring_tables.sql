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