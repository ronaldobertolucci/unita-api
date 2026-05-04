CREATE TABLE bank_account_types
(
    id   BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE
);

INSERT INTO bank_account_types (id, name)
VALUES (1, 'Corrente'),
       (2, 'Poupança'),
       (3, 'Salário'),
       (4, 'Investimento');

SELECT setval('bank_account_types_id_seq', 4);

CREATE TABLE benefit_types
(
    id   BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE
);

INSERT INTO benefit_types (id, name)
VALUES (1, 'Vale-Alimentação'),
       (2, 'Vale-Refeição'),
       (3, 'Premiação');

SELECT setval('benefit_types_id_seq', 3);

CREATE TABLE card_brands
(
    id   BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE
);

INSERT INTO card_brands (id, name)
VALUES (1, 'Visa'),
       (2, 'Mastercard'),
       (3, 'Elo'),
       (4, 'American Express'),
       (5, 'Hipercard');

SELECT setval('card_brands_id_seq', 5);