package io.github.ronaldobertolucci.unita.repository;

import io.github.ronaldobertolucci.unita.model.pocket.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    @Query("""
            SELECT t FROM Transaction t
            WHERE t.pocket.id = :pocketId
            ORDER BY t.transactionDate DESC
            """)
    List<Transaction> findAllByPocketId(@Param("pocketId") Long pocketId);

    @Query("""
            SELECT t FROM Transaction t
            WHERE t.id = :id
            AND t.pocket.id = :pocketId
            """)
    Optional<Transaction> findByIdAndPocketId(@Param("id") Long id, @Param("pocketId") Long pocketId);

    @Query("""
            SELECT COALESCE(SUM(CASE WHEN t.direction = 'INCOME' THEN t.amount ELSE -t.amount END), 0)
            FROM Transaction t
            WHERE t.pocket.id = :pocketId
            """)
    BigDecimal calculateBalanceByPocketId(@Param("pocketId") Long pocketId);
}