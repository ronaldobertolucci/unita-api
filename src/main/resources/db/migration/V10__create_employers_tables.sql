CREATE TABLE employers
(
    id   BIGSERIAL PRIMARY KEY,
    type VARCHAR(15) NOT NULL CHECK (type IN ('INDIVIDUAL', 'LEGAL_ENTITY'))
);

CREATE TABLE individual_employers
(
    employer_id BIGINT PRIMARY KEY,
    cpf         VARCHAR(11)  NOT NULL,
    name        VARCHAR(255) NOT NULL,
    CONSTRAINT fk_individual_employers FOREIGN KEY (employer_id) REFERENCES employers (id) ON DELETE CASCADE
);

CREATE TABLE legal_entity_employers
(
    employer_id     BIGINT NOT NULL PRIMARY KEY,
    legal_entity_id BIGINT NOT NULL,
    CONSTRAINT fk_legal_entity_employers FOREIGN KEY (employer_id) REFERENCES employers (id) ON DELETE CASCADE,
    CONSTRAINT fk_legal_entity_employers_legal_entity FOREIGN KEY (legal_entity_id) REFERENCES legal_entities (id)
);