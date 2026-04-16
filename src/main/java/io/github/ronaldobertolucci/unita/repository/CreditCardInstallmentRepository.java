package io.github.ronaldobertolucci.unita.repository;

import io.github.ronaldobertolucci.unita.model.card.CreditCardInstallment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface CreditCardInstallmentRepository extends JpaRepository<CreditCardInstallment, Long> {

    @Query("""
            SELECT i FROM CreditCardInstallment i
            JOIN FETCH i.creditCardBill
            WHERE i.purchase.id = :purchaseId
            ORDER BY i.installmentNumber ASC
            """)
    List<CreditCardInstallment> findAllByPurchaseId(@Param("purchaseId") Long purchaseId);

    @Query("""
            SELECT i FROM CreditCardInstallment i
            JOIN FETCH i.purchase
            WHERE i.creditCardBill.id = :billId
            ORDER BY i.installmentNumber ASC
            """)
    List<CreditCardInstallment> findAllByBillId(@Param("billId") Long billId);

    @Query("""
            SELECT COALESCE(SUM(i.amount), 0)
            FROM CreditCardInstallment i
            WHERE i.creditCardBill.id = :billId
            """)
    BigDecimal sumAmountByBillId(@Param("billId") Long billId);

    @Query("""
            SELECT i FROM CreditCardInstallment i
            WHERE i.id = :id
            AND i.purchase.id = :purchaseId
            """)
    Optional<CreditCardInstallment> findByIdAndPurchaseId(
            @Param("id") Long id,
            @Param("purchaseId") Long purchaseId);

    @Modifying
    @Transactional
    @Query("""
            DELETE FROM CreditCardInstallment i
            WHERE i.purchase.id = :purchaseId
            """)
    void deleteAllByPurchaseId(@Param("purchaseId") Long purchaseId);

    @Query("""
        SELECT COALESCE(SUM(i.amount), 0)
        FROM CreditCardInstallment i
        WHERE i.creditCardBill.creditCard.user.id = :userId
        AND i.creditCardBill.status = 'OPEN'
        """)
    BigDecimal sumInstallmentsByUserIdAndOpenBills(@Param("userId") Long userId);

    @Query(value = """
        SELECT c.name, COALESCE(SUM(i.amount), 0)
        FROM credit_card_installments i
        JOIN categories c ON c.id = i.category_id
        JOIN credit_card_purchases p ON p.id = i.purchase_id
        JOIN credit_cards cc ON cc.id = p.credit_card_id
        WHERE cc.user_id = :userId
        AND c.type = :categoryType
        AND (CAST(:startDate AS DATE) IS NULL OR i.installment_date >= :startDate)
        AND (CAST(:endDate AS DATE) IS NULL OR i.installment_date <= :endDate)
        GROUP BY c.name
        ORDER BY c.name ASC
        """, nativeQuery = true)
    List<Object[]> sumAmountByCategoryTypeAndUserIdAndPeriod(
            @Param("userId") Long userId,
            @Param("categoryType") String categoryType,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    @Query(value = """
        SELECT TO_CHAR(i.installment_date, 'YYYY-MM'), COALESCE(SUM(i.amount), 0)
        FROM credit_card_installments i
        JOIN categories c ON c.id = i.category_id
        JOIN credit_card_purchases p ON p.id = i.purchase_id
        JOIN credit_cards cc ON cc.id = p.credit_card_id
        WHERE cc.user_id = :userId
        AND c.type = 'EXPENSE'
        AND (CAST(:startDate AS DATE) IS NULL OR i.installment_date >= :startDate)
        AND (CAST(:endDate AS DATE) IS NULL OR i.installment_date <= :endDate)
        GROUP BY TO_CHAR(i.installment_date, 'YYYY-MM')
        ORDER BY TO_CHAR(i.installment_date, 'YYYY-MM') ASC
        """, nativeQuery = true)
    List<Object[]> sumExpenseAmountByMonthAndUserIdAndPeriod(
            @Param("userId") Long userId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);
}