package io.github.ronaldobertolucci.unita.repository;

import io.github.ronaldobertolucci.unita.model.card.CreditCardBill;
import io.github.ronaldobertolucci.unita.model.card.CreditCardBillStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface CreditCardBillRepository extends JpaRepository<CreditCardBill, Long> {

    @Query("""
            SELECT b FROM CreditCardBill b
            WHERE b.creditCard.id = :creditCardId
            ORDER BY b.closingDate ASC
            """)
    List<CreditCardBill> findAllByCreditCardId(@Param("creditCardId") Long creditCardId);

    @Query("""
            SELECT b FROM CreditCardBill b
            WHERE b.creditCard.id = :creditCardId
            AND b.status = :status
            ORDER BY b.closingDate ASC
            """)
    List<CreditCardBill> findAllByCreditCardIdAndStatus(
            @Param("creditCardId") Long creditCardId,
            @Param("status") CreditCardBillStatus status);

    @Query("""
            SELECT b FROM CreditCardBill b
            WHERE b.creditCard.id = :creditCardId
            AND b.closingDate >= :purchaseDate
            ORDER BY b.closingDate ASC
            """)
    List<CreditCardBill> findFirstByCreditCardIdAndClosingDateAfterPurchaseDate(
            @Param("creditCardId") Long creditCardId,
            @Param("purchaseDate") LocalDate purchaseDate,
            Pageable pageable);

    @Query("""
            SELECT b FROM CreditCardBill b
            WHERE b.id = :id
            AND b.creditCard.id = :creditCardId
            """)
    Optional<CreditCardBill> findByIdAndCreditCardId(
            @Param("id") Long id,
            @Param("creditCardId") Long creditCardId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("""
            UPDATE CreditCardBill b
            SET b.status = :newStatus
            WHERE b.closingDate < :today
            AND b.status = :currentStatus
            """)
    int closeAllOverdue(
            @Param("today") LocalDate today,
            @Param("currentStatus") CreditCardBillStatus currentStatus,
            @Param("newStatus") CreditCardBillStatus newStatus);
}