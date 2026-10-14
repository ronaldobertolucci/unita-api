-- Diagnóstico somente de leitura. Executar em snapshot consistente e acesso restrito.
-- Não é migration; não corrige composição, pagamentos nem saldos.
-- IDs e valores deste relatório são dados financeiros operacionais restritos.
WITH installments AS (
    SELECT credit_card_bill_id AS bill_id, SUM(amount) AS total
    FROM credit_card_installments GROUP BY credit_card_bill_id
), refunds AS (
    SELECT credit_card_bill_id AS bill_id, SUM(amount) AS total
    FROM credit_card_refunds GROUP BY credit_card_bill_id
), inventory AS (
    SELECT b.id AS bill_id, b.credit_card_id, c.user_id, b.status,
           b.payment_transaction_id,
           COALESCE(i.total, 0) - COALESCE(r.total, 0) AS composition_total,
           t.amount AS payment_amount, t.direction AS payment_direction,
           CASE
               WHEN b.status = 'PAID' AND t.id IS NULL THEN 'PAID_WITHOUT_PAYMENT'
               WHEN b.status <> 'PAID' AND t.id IS NOT NULL THEN 'PAYMENT_WITH_NON_PAID_STATUS'
               WHEN b.status = 'PAID' AND (t.direction <> 'EXPENSE' OR p.user_id <> c.user_id)
                   THEN 'INVALID_PAYMENT_DIRECTION_OR_OWNER'
               WHEN b.status = 'PAID' AND t.amount <> COALESCE(i.total, 0) - COALESCE(r.total, 0)
                   THEN 'PAYMENT_COMPOSITION_MISMATCH'
           END AS reason
    FROM credit_card_bills b
    JOIN credit_cards c ON c.id = b.credit_card_id
    LEFT JOIN installments i ON i.bill_id = b.id
    LEFT JOIN refunds r ON r.bill_id = b.id
    LEFT JOIN transactions t ON t.id = b.payment_transaction_id
    LEFT JOIN pockets p ON p.id = t.pocket_id
)
SELECT * FROM inventory WHERE reason IS NOT NULL ORDER BY user_id, credit_card_id, bill_id;
