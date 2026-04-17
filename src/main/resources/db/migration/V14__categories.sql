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

INSERT INTO categories (user_id, name, type, is_system)
VALUES (NULL, 'Pagamento de Cartão', 'NEUTRAL', true),
       (NULL, 'Transferência Enviada', 'NEUTRAL', true),
       (NULL, 'Transferência Recebida', 'NEUTRAL', true);

INSERT INTO categories (user_id, name, type, is_system)
VALUES (NULL, 'Alimentação', 'EXPENSE', false),
       (NULL, 'Transporte', 'EXPENSE', false),
       (NULL, 'Saúde', 'EXPENSE', false),
       (NULL, 'Educação', 'EXPENSE', false),
       (NULL, 'Lazer', 'EXPENSE', false),
       (NULL, 'Vestuário', 'EXPENSE', false),
       (NULL, 'Assinaturas', 'EXPENSE', false),
       (NULL, 'Aluguel', 'EXPENSE', false),
       (NULL, 'Energia', 'EXPENSE', false),
       (NULL, 'Água', 'EXPENSE', false),
       (NULL, 'Gás', 'EXPENSE', false),
       (NULL, 'Internet', 'EXPENSE', false),
       (NULL, 'Telefone', 'EXPENSE', false),
       (NULL, 'Beleza', 'EXPENSE', false),
       (NULL, 'Impostos', 'EXPENSE', false),
       (NULL, 'Restaurantes', 'EXPENSE', false),
       (NULL, 'Mercado', 'EXPENSE', false),
       (NULL, 'Viagem', 'EXPENSE', false),
       (NULL, 'Presentes', 'EXPENSE', false),
       (NULL, 'Eletrônicos', 'EXPENSE', false),
       (NULL, 'Eletrodomésticos', 'EXPENSE', false),
       (NULL, 'Brinquedos', 'EXPENSE', false),
       (NULL, 'Móveis', 'EXPENSE', false),
       (NULL, 'Salário', 'INCOME', false),
       (NULL, 'Freelance', 'INCOME', false),
       (NULL, '13º Salário', 'INCOME', false),
       (NULL, 'Rescisões', 'INCOME', false),
       (NULL, 'Bônus / PLR', 'INCOME', false),
       (NULL, 'Férias', 'INCOME', false),
       (NULL, 'Benefícios', 'INCOME', false),
       (NULL, 'Vendas', 'INCOME', false),
       (NULL, 'Reembolsos', 'INCOME', false),
       (NULL, 'Pensão / Mesada', 'INCOME', false),
       (NULL, 'Ajuste de Saldo', 'NEUTRAL', false);

ALTER TABLE transactions
    ADD COLUMN category_id BIGINT NOT NULL
        CONSTRAINT fk_transactions_category REFERENCES categories (id);

ALTER TABLE credit_card_installments
    ADD COLUMN category_id BIGINT NOT NULL
        CONSTRAINT fk_credit_card_installments_category REFERENCES categories (id);

ALTER TABLE credit_card_refunds
    ADD COLUMN category_id BIGINT NOT NULL
        CONSTRAINT fk_credit_card_refunds_category REFERENCES categories (id);