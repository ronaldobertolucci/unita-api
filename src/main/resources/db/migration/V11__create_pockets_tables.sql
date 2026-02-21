CREATE TABLE pockets
(
    id      BIGSERIAL PRIMARY KEY,
    dtype   VARCHAR(30) NOT NULL,
    user_id BIGINT      NOT NULL,
    CONSTRAINT fk_pockets_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE TABLE bank_accounts
(
    pocket_id            BIGINT PRIMARY KEY,
    legal_entity_id      BIGINT      NOT NULL,
    number               VARCHAR(20) NOT NULL,
    agency               VARCHAR(10) NOT NULL,
    bank_account_type_id BIGINT      NOT NULL,
    status               VARCHAR(10) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'INACTIVE', 'BLOCKED')),
    CONSTRAINT fk_bank_accounts FOREIGN KEY (pocket_id) REFERENCES pockets (id) ON DELETE CASCADE,
    CONSTRAINT fk_bank_accounts_legal_entity FOREIGN KEY (legal_entity_id) REFERENCES legal_entities (id),
    CONSTRAINT fk_bank_accounts_type FOREIGN KEY (bank_account_type_id) REFERENCES bank_account_types (id)
);

CREATE TABLE benefit_accounts
(
    pocket_id       BIGINT      NOT NULL PRIMARY KEY,
    legal_entity_id BIGINT      NOT NULL,
    benefit_type_id BIGINT      NOT NULL,
    status          VARCHAR(10) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'INACTIVE')),
    CONSTRAINT fk_benefit_accounts FOREIGN KEY (pocket_id) REFERENCES pockets (id) ON DELETE CASCADE,
    CONSTRAINT fk_benefit_accounts_legal_entity FOREIGN KEY (legal_entity_id) REFERENCES legal_entities (id),
    CONSTRAINT fk_benefit_accounts_type FOREIGN KEY (benefit_type_id) REFERENCES benefit_types (id)
);

CREATE TABLE fgts_employer_accounts
(
    pocket_id      BIGINT      NOT NULL PRIMARY KEY,
    employer_id    BIGINT      NOT NULL,
    admission_date DATE        NOT NULL,
    dismissal_date DATE,
    status         VARCHAR(10) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'INACTIVE')),
    CONSTRAINT fk_fgts_employer_accounts FOREIGN KEY (pocket_id) REFERENCES pockets (id) ON DELETE CASCADE,
    CONSTRAINT fk_fgts_employer_accounts_employer FOREIGN KEY (employer_id) REFERENCES employers (id)
);

CREATE TABLE cash
(
    pocket_id BIGINT NOT NULL PRIMARY KEY,
    CONSTRAINT fk_cash FOREIGN KEY (pocket_id) REFERENCES pockets (id) ON DELETE CASCADE
);