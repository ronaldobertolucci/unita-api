-- Enums como CHECK constraints
CREATE TABLE assets
(
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT       NOT NULL,
    legal_entity_id BIGINT       NOT NULL,
    name            VARCHAR(255) NOT NULL,
    category        VARCHAR(15)  NOT NULL CHECK (category IN ('RENDA_FIXA', 'PREVIDENCIA')),
    status          VARCHAR(10)  NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'MATURED', 'REDEEMED')),
    CONSTRAINT fk_assets_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_assets_legal_entity FOREIGN KEY (legal_entity_id) REFERENCES legal_entities (id)
);
CREATE INDEX idx_assets_user_id ON assets (user_id);

CREATE TABLE fixed_income_details
(
    asset_id      BIGINT PRIMARY KEY,
    indexer       VARCHAR(10)    NOT NULL CHECK (indexer IN ('CDI', 'IPCA', 'SELIC', 'PREFIXADO')),
    annual_rate   DECIMAL(11, 8) NOT NULL,
    maturity_date DATE           NOT NULL,
    is_tax_free   BOOLEAN        NOT NULL DEFAULT false,
    CONSTRAINT fk_fixed_income_details_asset FOREIGN KEY (asset_id) REFERENCES assets (id) ON DELETE CASCADE
);

CREATE TABLE pension_details
(
    asset_id     BIGINT PRIMARY KEY,
    pension_type VARCHAR(20)  NOT NULL CHECK (pension_type IN ('PGBL', 'VGBL', 'ENTIDADE_FECHADA')),
    tax_regime   VARCHAR(12) NOT NULL CHECK (tax_regime IN ('PROGRESSIVO', 'REGRESSIVO')),
    CONSTRAINT fk_pension_details_asset FOREIGN KEY (asset_id) REFERENCES assets (id) ON DELETE CASCADE
);

CREATE TABLE investment_positions
(
    id                  BIGSERIAL PRIMARY KEY,
    asset_id            BIGINT         NOT NULL UNIQUE,
    quantity            DECIMAL(18, 8) NOT NULL DEFAULT 0,
    average_price       DECIMAL(18, 8) NOT NULL DEFAULT 0,
    total_invested      DECIMAL(18, 8) NOT NULL DEFAULT 0,
    current_value       DECIMAL(18, 8) NOT NULL DEFAULT 0,
    redeemed_value      DECIMAL(18, 8) NOT NULL DEFAULT 0,
    last_valuation_date DATE,
    CONSTRAINT fk_investment_positions_asset FOREIGN KEY (asset_id) REFERENCES assets (id) ON DELETE CASCADE
);

CREATE TABLE investment_transactions
(
    id               BIGSERIAL PRIMARY KEY,
    asset_id         BIGINT         NOT NULL,
    transaction_id   BIGINT, -- FK nullable para Transaction do pocket
    type             VARCHAR(5)     NOT NULL CHECK (type IN ('BUY', 'SELL', 'YIELD', 'TAX')),
    amount           DECIMAL(18, 8) NOT NULL,
    transaction_date DATE           NOT NULL,
    notes            VARCHAR(500),
    CONSTRAINT fk_investment_transactions_asset FOREIGN KEY (asset_id) REFERENCES assets (id) ON DELETE CASCADE,
    CONSTRAINT fk_investment_transactions_transaction FOREIGN KEY (transaction_id) REFERENCES transactions (id) ON DELETE SET NULL
);
CREATE INDEX idx_investment_transactions_asset_id ON investment_transactions (asset_id);

-- Categorias de sistema para investimentos
INSERT INTO categories (user_id, name, type, is_system)
VALUES (NULL, 'Aporte em Investimento', 'NEUTRAL', true),
       (NULL, 'Rendimento de Investimento', 'NEUTRAL', true),
       (NULL, 'Resgate de Investimento', 'NEUTRAL', true);