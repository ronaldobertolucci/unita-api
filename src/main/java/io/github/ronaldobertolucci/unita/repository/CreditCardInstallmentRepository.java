package io.github.ronaldobertolucci.unita.repository;

import io.github.ronaldobertolucci.unita.model.card.CreditCardInstallment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
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
}