-- Current API contract fixture with explicitly corrected synthetic institutional links.
-- The original invalid references remain in legacy-baseline.sql for pre-V25 audit/migration tests.
-- Explicit IDs intentionally do not advance identity sequences. No real data/credentials.
INSERT INTO users (id,first_name,last_name,email,password,date_of_birth,enabled,email_verified) VALUES
 (91001,'Synthetic','Owner A','inv001-a@example.invalid','not-a-login-hash',DATE '1990-01-01',true,true),
 (91002,'Synthetic','Owner B','inv001-b@example.invalid','not-a-login-hash',DATE '1991-02-02',true,true);
INSERT INTO user_roles (user_id,role_id) VALUES
 (91001,(SELECT id FROM roles WHERE name='USER')),
 (91002,(SELECT id FROM roles WHERE name='USER'));
INSERT INTO legal_entities (id,user_id,cnpj,corporate_name,trade_name,state_registration) VALUES
 (92001,91001,'12345678000190','Synthetic issuer A','Issuer A',NULL),
 (92002,91001,'98765432000100','Synthetic custodian A',NULL,'SYNTHETIC'),
 (92003,91002,'12345678000190','Synthetic issuer B','Issuer B',NULL);
INSERT INTO pockets (id,dtype,user_id) VALUES
 (94001,'CASH',91001),(94002,'CASH',91001),(94003,'CASH',91001),
 (94004,'CASH',91002),(94005,'CASH',91002);
INSERT INTO cash (pocket_id) VALUES (94001),(94002),(94003),(94004),(94005);
INSERT INTO assets (id,user_id,legal_entity_id,custodian_legal_entity_id,name,category,status,liquidity_type) VALUES
 (93001,91001,92001,92002,'S01 full redemption','RENDA_FIXA','REDEEMED','DIARIA'),
 -- Explicitly corrected the synthetic orphan/foreign custodian; monetary/history fields are unchanged.
 (93002,91001,92001,92002,'S14 multiple buys and stale valuation','RENDA_FIXA','ACTIVE','NO_VENCIMENTO'),
 (93003,91001,92001,92002,'S14 matured pension with foreign custodian','PREVIDENCIA','MATURED','PREVIDENCIARIA'),
 (93004,91001,92001,NULL,'S14 missing position','PREVIDENCIA','ACTIVE',NULL),
 (93005,91002,92003,NULL,'S14 owner B eight decimals','RENDA_FIXA','ACTIVE','DIARIA');
INSERT INTO fixed_income_details (asset_id,indexer,annual_rate,maturity_date,is_tax_free) VALUES
 (93001,'PREFIXADO',0.10000000,DATE '2026-10-01',false),
 (93002,'CDI',1.10000000,DATE '2027-10-01',false),
 (93005,'IPCA',0.05123456,DATE '2028-10-01',true);
INSERT INTO pension_details (asset_id,pension_type,tax_regime) VALUES
 (93003,'VGBL','REGRESSIVO'),(93004,'PGBL','PROGRESSIVO');
INSERT INTO investment_positions (id,asset_id,quantity,average_price,total_invested,current_value,redeemed_value,last_valuation_date) VALUES
 (96001,93001,0,0,1000,0,1085,DATE '2026-10-12'),
 (96002,93002,5.12345678,97.59036012,500.12345678,550.87654321,12.34,DATE '2025-12-31'),
 (96003,93003,2,100,200,220,0,DATE '2026-01-31'),
 (96004,93005,0.33333333,100,33.33333333,35.12345678,0,NULL);
INSERT INTO transactions (id,pocket_id,amount,direction,transaction_date,description,category_id) VALUES
 (95001,94001,2000,'INCOME',DATE '2026-01-01','Synthetic opening cash',3),
 (95002,94001,1000,'EXPENSE',DATE '2026-02-01','S01 contribution',39),
 (95003,94001,1085,'INCOME',DATE '2026-10-12','S01 net proceeds; withholding already deducted',41),
 (95004,94001,200,'EXPENSE',DATE '2026-03-01','S14 first contribution',39),
 (95005,94001,300,'EXPENSE',DATE '2026-04-01','S14 second contribution rounded to cash cents',39),
 (95006,94001,12.34,'INCOME',DATE '2026-10-12','S14 yield',40),
 (95007,94001,15.12,'INCOME',DATE '2026-10-12','S14 cash remains after reference was detached',40),
 (95008,94002,50,'EXPENSE',DATE '2026-03-01','Synthetic negative cash is preserved',2),
 (95009,94004,100,'INCOME',DATE '2026-01-01','Synthetic owner B cash',3),
 (95010,94004,33.33,'EXPENSE',DATE '2026-02-02','S14 eight decimals rounded only in legacy cash',39);
INSERT INTO investment_transactions (id,asset_id,transaction_id,type,amount,transaction_date,notes) VALUES
 (97001,93001,95002,'BUY',1000,DATE '2026-02-01','S01 principal 1000'),
 (97002,93001,95003,'SELL',1100,DATE '2026-10-12','S01 gross 1100; net cash 1085'),
 (97003,93001,NULL,'TAX',15,DATE '2026-10-12','S01 supplied tax; no explicit SELL link'),
 (97004,93002,95004,'BUY',200,DATE '2026-03-01','S14 first buy'),
 (97005,93002,95005,'BUY',300.12345678,DATE '2026-04-01','S14 eight-decimal second buy'),
 (97006,93002,95006,'YIELD',12.34,DATE '2026-10-12','S14 same-day yield'),
 (97007,93002,NULL,'YIELD',15.12345678,DATE '2026-10-12','S14 detached cash reference; keep original notes: "á"'),
 (97008,93002,95006,'SELL',5,DATE '2026-10-12','S14 ambiguous shared cash reference'),
 (97009,93002,NULL,'TAX',1,DATE '2026-10-12','S14 same-day tax is not paired by proximity'),
 (97010,93003,NULL,'BUY',200,DATE '2026-01-31',NULL),
 (97011,93005,95010,'BUY',33.33333333,DATE '2026-02-02','S14 owner B precision');
