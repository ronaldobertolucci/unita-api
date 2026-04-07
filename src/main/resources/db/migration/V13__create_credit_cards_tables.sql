CREATE TABLE credit_cards
(
    id               BIGSERIAL PRIMARY KEY,
    user_id          BIGINT         NOT NULL,
    legal_entity_id  BIGINT         NOT NULL,
    last_four_digits VARCHAR(4)     NOT NULL,
    card_brand_id    BIGINT         NOT NULL,
    credit_limit     NUMERIC(15, 2) NOT NULL,
    closing_day      SMALLINT       NOT NULL CHECK (closing_day BETWEEN 1 AND 31),
    due_day          SMALLINT       NOT NULL CHECK (due_day BETWEEN 1 AND 31),
    CONSTRAINT fk_credit_cards_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_credit_cards_legal_entity FOREIGN KEY (legal_entity_id) REFERENCES legal_entities (id),
    CONSTRAINT fk_credit_cards_brand FOREIGN KEY (card_brand_id) REFERENCES card_brands (id)
);

CREATE TABLE credit_card_bills
(
    id                     BIGSERIAL PRIMARY KEY,
    credit_card_id         BIGINT      NOT NULL,
    period_start           DATE        NOT NULL,
    closing_date           DATE        NOT NULL,
    due_date               DATE        NOT NULL,
    closing_day            SMALLINT    NOT NULL,
    due_day                SMALLINT    NOT NULL,
    status                 VARCHAR(10) NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN', 'CLOSED', 'PAID')),
    payment_transaction_id BIGINT UNIQUE,
    CONSTRAINT fk_credit_card_bills_card FOREIGN KEY (credit_card_id) REFERENCES credit_cards (id) ON DELETE CASCADE,
    CONSTRAINT fk_credit_card_bills_payment FOREIGN KEY (payment_transaction_id) REFERENCES transactions (id) ON DELETE SET NULL
);

CREATE TABLE credit_card_purchases
(
    id                 BIGSERIAL PRIMARY KEY,
    credit_card_id     BIGINT         NOT NULL,
    description        VARCHAR(255)   NOT NULL,
    total_value        NUMERIC(15, 2) NOT NULL,
    purchase_date      DATE           NOT NULL,
    installments_count INT            NOT NULL DEFAULT 1,
    CONSTRAINT fk_credit_card_purchases_card FOREIGN KEY (credit_card_id) REFERENCES credit_cards (id) ON DELETE CASCADE
);

CREATE TABLE credit_card_installments
(
    id                  BIGSERIAL PRIMARY KEY,
    purchase_id         BIGINT         NOT NULL,
    installment_number  INT            NOT NULL,
    amount              NUMERIC(15, 2) NOT NULL,
    credit_card_bill_id BIGINT         NOT NULL,
    CONSTRAINT fk_credit_card_installments_purchase FOREIGN KEY (purchase_id) REFERENCES credit_card_purchases (id) ON DELETE CASCADE,
    CONSTRAINT fk_credit_card_installments_bill FOREIGN KEY (credit_card_bill_id) REFERENCES credit_card_bills (id),
    CONSTRAINT uk_credit_card_installments UNIQUE (purchase_id, installment_number)
);

CREATE TABLE credit_card_refunds
(
    id                  BIGSERIAL PRIMARY KEY,
    credit_card_bill_id BIGINT         NOT NULL,
    description         VARCHAR(255)   NOT NULL,
    amount              NUMERIC(15, 2) NOT NULL,
    refund_date         DATE           NOT NULL,
    CONSTRAINT fk_credit_card_refunds_bill FOREIGN KEY (credit_card_bill_id) REFERENCES credit_card_bills (id) ON DELETE CASCADE
);

CREATE INDEX idx_credit_card_bills_card_status ON credit_card_bills (credit_card_id, status);
CREATE INDEX idx_credit_card_purchases_card_date ON credit_card_purchases (credit_card_id, purchase_date);