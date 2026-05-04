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

INSERT INTO categories (id, user_id, name, type, is_system)
VALUES (1, NULL, 'Pagamento de Cartão', 'NEUTRAL', true),
       (2, NULL, 'Transferência Enviada', 'NEUTRAL', true),
       (3,NULL, 'Transferência Recebida', 'NEUTRAL', true);

INSERT INTO categories (id, user_id, name, type, is_system)
VALUES (4, NULL, 'Alimentação', 'EXPENSE', false),
       (5, NULL, 'Transporte', 'EXPENSE', false),
       (6, NULL, 'Saúde', 'EXPENSE', false),
       (7, NULL, 'Educação', 'EXPENSE', false),
       (8, NULL, 'Lazer', 'EXPENSE', false),
       (9, NULL, 'Vestuário', 'EXPENSE', false),
       (10, NULL, 'Assinaturas', 'EXPENSE', false),
       (11, NULL, 'Aluguel', 'EXPENSE', false),
       (12, NULL, 'Energia', 'EXPENSE', false),
       (13, NULL, 'Água', 'EXPENSE', false),
       (14, NULL, 'Gás', 'EXPENSE', false),
       (15, NULL, 'Internet', 'EXPENSE', false),
       (16, NULL, 'Telefone', 'EXPENSE', false),
       (17, NULL, 'Beleza', 'EXPENSE', false),
       (18, NULL, 'Impostos', 'EXPENSE', false),
       (19, NULL, 'Restaurantes', 'EXPENSE', false),
       (20, NULL, 'Mercado', 'EXPENSE', false),
       (21, NULL, 'Viagem', 'EXPENSE', false),
       (22, NULL, 'Presentes', 'EXPENSE', false),
       (23, NULL, 'Eletrônicos', 'EXPENSE', false),
       (24, NULL, 'Eletrodomésticos', 'EXPENSE', false),
       (25, NULL, 'Brinquedos', 'EXPENSE', false),
       (26, NULL, 'Móveis', 'EXPENSE', false),
       (27, NULL, 'Juros de mora', 'EXPENSE', false),
       (28, NULL, 'Salário', 'INCOME', false),
       (29, NULL, 'Freelance', 'INCOME', false),
       (30, NULL, '13º Salário', 'INCOME', false),
       (31, NULL, 'Rescisões', 'INCOME', false),
       (32, NULL, 'Bônus / PLR', 'INCOME', false),
       (33, NULL, 'Férias', 'INCOME', false),
       (34, NULL, 'Benefícios', 'INCOME', false),
       (35, NULL, 'Vendas', 'INCOME', false),
       (36, NULL, 'Reembolsos', 'INCOME', false),
       (37, NULL, 'Pensão / Mesada', 'INCOME', false),
       (38, NULL, 'Ajuste de Saldo', 'NEUTRAL', false);

SELECT setval('categories_id_seq', 38);

ALTER TABLE transactions
    ADD COLUMN category_id BIGINT NOT NULL
        CONSTRAINT fk_transactions_category REFERENCES categories (id);

ALTER TABLE credit_card_installments
    ADD COLUMN category_id BIGINT NOT NULL
        CONSTRAINT fk_credit_card_installments_category REFERENCES categories (id);

ALTER TABLE credit_card_refunds
    ADD COLUMN category_id BIGINT NOT NULL
        CONSTRAINT fk_credit_card_refunds_category REFERENCES categories (id);