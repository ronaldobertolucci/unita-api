-- Diagnóstico somente de leitura. Executar com acesso operacional restrito.
-- Não retorna nomes, CPF/CNPJ ou e-mails; não modifica registros.
SELECT 'BANK_ACCOUNT' AS resource_type, p.id AS resource_id,
       p.user_id AS resource_owner_id, le.id AS registration_id,
       le.user_id AS registration_owner_id
FROM bank_accounts a JOIN pockets p ON p.id = a.pocket_id
JOIN legal_entities le ON le.id = a.legal_entity_id
WHERE p.user_id <> le.user_id
UNION ALL
SELECT 'BENEFIT_ACCOUNT', p.id, p.user_id, le.id, le.user_id
FROM benefit_accounts a JOIN pockets p ON p.id = a.pocket_id
JOIN legal_entities le ON le.id = a.legal_entity_id
WHERE p.user_id <> le.user_id
UNION ALL
SELECT 'FGTS_EMPLOYER_ACCOUNT', p.id, p.user_id, e.id, e.user_id
FROM fgts_employer_accounts a JOIN pockets p ON p.id = a.pocket_id
JOIN employers e ON e.id = a.employer_id
WHERE p.user_id <> e.user_id
UNION ALL
SELECT 'CREDIT_CARD', c.id, c.user_id, le.id, le.user_id
FROM credit_cards c JOIN legal_entities le ON le.id = c.legal_entity_id
WHERE c.user_id <> le.user_id
UNION ALL
-- Empregadores PJ legados podem expor uma PJ de outro dono por meio do FGTS.
SELECT 'LEGAL_ENTITY_EMPLOYER', e.id, e.user_id, le.id, le.user_id
FROM legal_entity_employers a JOIN employers e ON e.id = a.employer_id
JOIN legal_entities le ON le.id = a.legal_entity_id
WHERE e.user_id <> le.user_id
ORDER BY resource_type, resource_id;
