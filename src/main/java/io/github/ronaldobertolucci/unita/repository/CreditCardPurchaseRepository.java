package io.github.ronaldobertolucci.unita.repository;

import io.github.ronaldobertolucci.unita.model.card.CreditCardPurchase;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CreditCardPurchaseRepository extends JpaRepository<CreditCardPurchase, Long> {

    @Query("""
            SELECT p FROM CreditCardPurchase p
            WHERE p.creditCard.id = :creditCardId
            ORDER BY p.purchaseDate DESC
            """)
    List<CreditCardPurchase> findAllByCreditCardId(@Param("creditCardId") Long creditCardId);

    @Query("""
            SELECT p FROM CreditCardPurchase p
            WHERE p.id = :id
            AND p.creditCard.id = :creditCardId
            """)
    Optional<CreditCardPurchase> findByIdAndCreditCardId(
            @Param("id") Long id,
            @Param("creditCardId") Long creditCardId);
}