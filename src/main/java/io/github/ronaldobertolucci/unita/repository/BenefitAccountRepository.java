package io.github.ronaldobertolucci.unita.repository;

import io.github.ronaldobertolucci.unita.model.pocket.BenefitAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface BenefitAccountRepository extends JpaRepository<BenefitAccount, Long> {

    @Query("""
            SELECT ba FROM BenefitAccount ba
            JOIN FETCH ba.legalEntity
            JOIN FETCH ba.benefitType
            WHERE ba.id = :id
            AND ba.user.id = :userId
            """)
    Optional<BenefitAccount> findByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);

    boolean existsByIdAndUserId(Long id, Long userId);

    boolean existsByLegalEntityId(Long legalEntityId);
}