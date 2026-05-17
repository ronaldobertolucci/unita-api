ALTER TABLE assets
    ADD COLUMN liquidity_type VARCHAR(20)
        CHECK (liquidity_type IN ('DIARIA', 'NO_VENCIMENTO', 'MERCADO', 'PRAZO_FIXO', 'PREVIDENCIARIA'));