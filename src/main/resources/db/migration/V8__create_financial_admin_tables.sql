CREATE TABLE bank_account_types
(
    id   BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE
);

INSERT INTO bank_account_types (name)
VALUES ('Corrente'),
       ('Poupança'),
       ('Salário'),
       ('Investimento');

CREATE TABLE benefit_types
(
    id   BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE
);

INSERT INTO benefit_types (name)
VALUES ('Vale-Alimentação'),
       ('Vale-Refeição');

CREATE TABLE card_brands
(
    id   BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE
);

INSERT INTO card_brands (name)
VALUES ('Visa'),
       ('Mastercard'),
       ('Elo'),
       ('American Express'),
       ('Hipercard');