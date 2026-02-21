package io.github.ronaldobertolucci.unita.repository;

import io.github.ronaldobertolucci.unita.model.pocket.FgtsEmployerAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface FgtsEmployerAccountRepository extends JpaRepository<FgtsEmployerAccount, Long> {

    @Query("""
            SELECT f FROM FgtsEmployerAccount f
            JOIN FETCH f.employer
            WHERE f.id = :id
            AND f.user.id = :userId
            """)
    Optional<FgtsEmployerAccount> findByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);

    boolean existsByIdAndUserId(Long id, Long userId);
}