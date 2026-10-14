-- Diagnóstico restrito, somente de leitura. Executar após V23; não executar no startup.
-- IDs internos apenas. Categorias nunca comprovam correlação entre dois lançamentos.
-- Relações comprovadas de novas transferências (inclui saque FGTS).
SELECT 'EXPLICIT_TRANSFER' AS classification, o.id AS operation_id, o.type,
       o.source_transaction_id, o.target_transaction_id
FROM transfer_operations o ORDER BY o.id;

-- Pagamentos comprovados, inclusive anteriores à V23. Referências nulas são inconsistências,
-- não autorização para reconstruir um débito.
SELECT CASE WHEN b.payment_transaction_id IS NULL THEN 'PAID_WITHOUT_REFERENCE'
            ELSE 'EXPLICIT_PAYMENT' END AS classification,
       b.id AS bill_id, b.payment_transaction_id
FROM credit_card_bills b
WHERE b.payment_transaction_id IS NOT NULL OR b.status = 'PAID'
ORDER BY b.id;

-- Inventário completo dos lançamentos sem vínculo protegido. Indício de categoria
-- é somente candidato para investigação: lançamentos manuais também usam categorias globais.
-- Não combinar por valor, descrição, data ou proprietário. Investimentos continuam fora do saneamento.
SELECT CASE WHEN EXISTS (SELECT 1 FROM investment_transactions i WHERE i.transaction_id = t.id)
                 THEN 'INVESTMENT_OUT_OF_SCOPE'
            WHEN c.is_system AND c.name IN ('Transferência Enviada', 'Transferência Recebida')
                 THEN 'AMBIGUOUS_TRANSFER_CANDIDATE'
            ELSE 'UNLINKED_ORIGIN_UNKNOWN' END AS classification,
       t.id AS transaction_id, t.pocket_id
FROM transactions t JOIN categories c ON c.id = t.category_id
WHERE NOT EXISTS (SELECT 1 FROM transfer_operations o
                  WHERE o.source_transaction_id = t.id OR o.target_transaction_id = t.id)
  AND NOT EXISTS (SELECT 1 FROM credit_card_bills b WHERE b.payment_transaction_id = t.id)
ORDER BY t.id;
