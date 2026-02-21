CREATE TABLE legal_entities
(
    id                 BIGSERIAL PRIMARY KEY,
    cnpj               VARCHAR(14)  NOT NULL UNIQUE,
    corporate_name     VARCHAR(255) NOT NULL,
    trade_name         VARCHAR(255),
    state_registration VARCHAR(50)
);