-- noinspection SqlAddNotNullColumnForFile
CREATE TABLE categories
(
    id        BIGSERIAL PRIMARY KEY,
    user_id   BIGINT,
    name      VARCHAR(100) NOT NULL,
    type      VARCHAR(10)  NOT NULL CHECK (type IN ('INCOME', 'EXPENSE', 'NEUTRAL')),
    is_system BOOLEAN      NOT NULL DEFAULT false,
    CONSTRAINT fk_categories_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE INDEX idx_categories_user_id ON categories (user_id);

-- Categorias de sistema (is_system = true, user_id = null)
INSERT INTO categories (user_id, name, type, is_system)
VALUES (NULL, 'Pagamento de Cartão', 'NEUTRAL', true),
       (NULL, 'Transferência Enviada', 'NEUTRAL', true),
       (NULL, 'Transferência Recebida', 'NEUTRAL', true);

-- Categorias globais padrão (is_system = false, user_id = null)
INSERT INTO categories (user_id, name, type, is_system)
VALUES (NULL, 'Alimentação', 'EXPENSE', false),
       (NULL, 'Transporte', 'EXPENSE', false),
       (NULL, 'Moradia', 'EXPENSE', false),
       (NULL, 'Saúde', 'EXPENSE', false),
       (NULL, 'Educação', 'EXPENSE', false),
       (NULL, 'Lazer', 'EXPENSE', false),
       (NULL, 'Vestuário', 'EXPENSE', false),
       (NULL, 'Assinaturas', 'EXPENSE', false),
       (NULL, 'Salário', 'INCOME', false),
       (NULL, 'Freelance', 'INCOME', false),
       (NULL, 'Ajuste de Saldo', 'NEUTRAL', false);

-- Adiciona category_id nas tabelas de movimentos financeiros
ALTER TABLE transactions
    ADD COLUMN category_id BIGINT NOT NULL
        CONSTRAINT fk_transactions_category REFERENCES categories (id);

ALTER TABLE credit_card_installments
    ADD COLUMN category_id BIGINT NOT NULL
        CONSTRAINT fk_credit_card_installments_category REFERENCES categories (id);

ALTER TABLE credit_card_refunds
    ADD COLUMN category_id BIGINT NOT NULL
        CONSTRAINT fk_credit_card_refunds_category REFERENCES categories (id);

ALTER TABLE recurring_transactions
    ADD COLUMN category_id BIGINT NOT NULL
        CONSTRAINT fk_recurring_transactions_category REFERENCES categories (id);

ALTER TABLE recurring_purchases
    ADD COLUMN category_id BIGINT NOT NULL
        CONSTRAINT fk_recurring_purchases_category REFERENCES categories (id);