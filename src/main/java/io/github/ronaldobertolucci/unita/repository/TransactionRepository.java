package io.github.ronaldobertolucci.unita.repository;

import io.github.ronaldobertolucci.unita.model.pocket.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    @Query(value = """
        SELECT * FROM transactions t
        WHERE t.pocket_id = :pocketId
        AND (CAST(:startDate AS DATE) IS NULL OR t.transaction_date >= :startDate)
        AND (CAST(:endDate AS DATE) IS NULL OR t.transaction_date <= :endDate)
        ORDER BY t.transaction_date DESC
        """, nativeQuery = true)
    List<Transaction> findAllByPocketIdAndPeriod(
            @Param("pocketId") Long pocketId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

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