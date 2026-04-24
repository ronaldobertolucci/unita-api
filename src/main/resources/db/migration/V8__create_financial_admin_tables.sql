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

CREATE TABLE benefit_types
(
    id   BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE
);

INSERT INTO benefit_types (id, name)
VALUES (1, 'Vale-Alimentação'),
       (2, 'Vale-Refeição'),
       (3, 'Premiação');

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