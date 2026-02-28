ALTER TABLE legal_entities ADD COLUMN user_id BIGINT NOT NULL;
ALTER TABLE legal_entities ADD CONSTRAINT fk_legal_entities_user
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;
CREATE INDEX idx_legal_entities_user_id ON legal_entities(user_id);

ALTER TABLE employers ADD COLUMN user_id BIGINT NOT NULL;
ALTER TABLE employers ADD CONSTRAINT fk_employers_user
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;
CREATE INDEX idx_employers_user_id ON employers(user_id);