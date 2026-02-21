package io.github.ronaldobertolucci.unita.repository;

import io.github.ronaldobertolucci.unita.model.pocket.RecurringTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface RecurringTransactionRepository extends JpaRepository<RecurringTransaction, Long> {

    @Query("""
            SELECT rt FROM RecurringTransaction rt
            JOIN FETCH rt.periodicity
            WHERE rt.pocket.id = :pocketId
            ORDER BY rt.startDate ASC
            """)
    List<RecurringTransaction> findAllByPocketId(@Param("pocketId") Long pocketId);

    @Query("""
            SELECT rt FROM RecurringTransaction rt
            JOIN FETCH rt.pocket
            JOIN FETCH rt.periodicity
            WHERE rt.startDate <= :today
            AND (rt.endDate IS NULL OR rt.endDate >= :today)
            """)
    List<RecurringTransaction> findAllActive(@Param("today") LocalDate today);

    @Query("""
            SELECT rt FROM RecurringTransaction rt
            WHERE rt.id = :id
            AND rt.pocket.id = :pocketId
            """)
    Optional<RecurringTransaction> findByIdAndPocketId(@Param("id") Long id, @Param("pocketId") Long pocketId);
}