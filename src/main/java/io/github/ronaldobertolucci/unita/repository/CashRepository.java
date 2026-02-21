package io.github.ronaldobertolucci.unita.repository;

import io.github.ronaldobertolucci.unita.model.pocket.Cash;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CashRepository extends JpaRepository<Cash, Long> {

    @Query("""
            SELECT c FROM Cash c
            WHERE c.user.id = :userId
            """)
    Optional<Cash> findByUserId(@Param("userId") Long userId);

    boolean existsByUserId(Long userId);
}