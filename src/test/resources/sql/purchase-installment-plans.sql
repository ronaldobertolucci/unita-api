-- Diagnóstico somente de leitura. Executar em snapshot consistente, acesso restrito.
-- Não infere datas, não completa parcelas nem corrige valores históricos.
WITH plan AS (
    SELECT i.purchase_id, COUNT(*) AS actual_count,
           COUNT(DISTINCT i.installment_number) AS distinct_numbers,
           MIN(i.installment_number) AS first_number, MAX(i.installment_number) AS last_number,
           SUM(i.amount) AS actual_total,
           SUM(CASE WHEN i.amount <= 0 THEN 1 ELSE 0 END) AS invalid_amounts,
           SUM(CASE WHEN b.credit_card_id <> p.credit_card_id THEN 1 ELSE 0 END) AS foreign_bills,
           SUM(CASE WHEN c.user_id IS NOT NULL AND c.user_id <> card.user_id THEN 1 ELSE 0 END) AS foreign_categories
    FROM credit_card_installments i
    JOIN credit_card_purchases p ON p.id = i.purchase_id
    JOIN credit_cards card ON card.id = p.credit_card_id
    JOIN credit_card_bills b ON b.id = i.credit_card_bill_id
    JOIN categories c ON c.id = i.category_id
    GROUP BY i.purchase_id
)
SELECT p.id AS purchase_id, p.credit_card_id, p.installments_count AS declared_count,
       COALESCE(plan.actual_count, 0) AS actual_count, p.total_value AS declared_total,
       COALESCE(plan.actual_total, 0) AS actual_total,
       CASE
           WHEN p.total_value <= 0 OR p.installments_count NOT BETWEEN 1 AND 120 THEN 'INVALID_PURCHASE'
           WHEN COALESCE(plan.actual_count, 0) <> p.installments_count THEN 'COUNT_MISMATCH'
           WHEN plan.distinct_numbers <> p.installments_count OR plan.first_number <> 1 OR plan.last_number <> p.installments_count THEN 'NUMBER_MISMATCH'
           WHEN plan.invalid_amounts > 0 THEN 'INVALID_INSTALLMENT_AMOUNT'
           WHEN plan.actual_total <> p.total_value THEN 'TOTAL_MISMATCH'
           WHEN plan.foreign_bills > 0 THEN 'FOREIGN_BILL'
           WHEN plan.foreign_categories > 0 THEN 'FOREIGN_CATEGORY'
       END AS reason
FROM credit_card_purchases p LEFT JOIN plan ON plan.purchase_id = p.id
WHERE p.total_value <= 0 OR p.installments_count NOT BETWEEN 1 AND 120
   OR COALESCE(plan.actual_count, 0) <> p.installments_count
   OR plan.distinct_numbers <> p.installments_count OR plan.first_number <> 1 OR plan.last_number <> p.installments_count
   OR plan.invalid_amounts > 0 OR plan.actual_total <> p.total_value
   OR plan.foreign_bills > 0 OR plan.foreign_categories > 0
ORDER BY p.id;
