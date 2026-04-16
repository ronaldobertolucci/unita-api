package io.github.ronaldobertolucci.unita.repository;

import io.github.ronaldobertolucci.unita.model.card.CreditCardRefund;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface CreditCardRefundRepository extends JpaRepository<CreditCardRefund, Long> {

    @Query("""
            SELECT r FROM CreditCardRefund r
            WHERE r.creditCardBill.id = :billId
            ORDER BY r.refundDate DESC
            """)
    List<CreditCardRefund> findAllByBillId(@Param("billId") Long billId);

    @Query("""
            SELECT COALESCE(SUM(r.amount), 0)
            FROM CreditCardRefund r
            WHERE r.creditCardBill.id = :billId
            """)
    BigDecimal sumAmountByBillId(@Param("billId") Long billId);

    @Query("""
            SELECT r FROM CreditCardRefund r
            WHERE r.id = :id
            AND r.creditCardBill.id = :billId
            """)
    Optional<CreditCardRefund> findByIdAndBillId(
            @Param("id") Long id,
            @Param("billId") Long billId);

    @Query("""
        SELECT COALESCE(SUM(r.amount), 0)
        FROM CreditCardRefund r
        WHERE r.creditCardBill.creditCard.user.id = :userId
        AND r.creditCardBill.status = 'OPEN'
        """)
    BigDecimal sumRefundsByUserIdAndOpenBills(@Param("userId") Long userId);
}