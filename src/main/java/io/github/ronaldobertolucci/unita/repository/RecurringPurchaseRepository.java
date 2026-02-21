package io.github.ronaldobertolucci.unita.repository;

import io.github.ronaldobertolucci.unita.model.card.RecurringPurchase;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface RecurringPurchaseRepository extends JpaRepository<RecurringPurchase, Long> {

    @Query("""
            SELECT rp FROM RecurringPurchase rp
            JOIN FETCH rp.periodicity
            WHERE rp.creditCard.id = :creditCardId
            ORDER BY rp.startDate ASC
            """)
    List<RecurringPurchase> findAllByCreditCardId(@Param("creditCardId") Long creditCardId);

    @Query("""
            SELECT rp FROM RecurringPurchase rp
            JOIN FETCH rp.creditCard
            JOIN FETCH rp.periodicity
            WHERE rp.startDate <= :today
            AND (rp.endDate IS NULL OR rp.endDate >= :today)
            """)
    List<RecurringPurchase> findAllActive(@Param("today") LocalDate today);

    @Query("""
            SELECT rp FROM RecurringPurchase rp
            WHERE rp.id = :id
            AND rp.creditCard.id = :creditCardId
            """)
    Optional<RecurringPurchase> findByIdAndCreditCardId(
            @Param("id") Long id,
            @Param("creditCardId") Long creditCardId);
}