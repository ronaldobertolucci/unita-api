package io.github.ronaldobertolucci.unita.repository;

import io.github.ronaldobertolucci.unita.model.investment.FixedIncomeDetails;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface FixedIncomeDetailsRepository extends JpaRepository<FixedIncomeDetails, Long> {

    Optional<FixedIncomeDetails> findByAssetId(Long assetId);

    @Query("SELECT f FROM FixedIncomeDetails f WHERE f.maturityDate <= :date AND f.asset.status = 'ACTIVE'")
    List<FixedIncomeDetails> findAllMaturedByDate(@Param("date") LocalDate date);
}