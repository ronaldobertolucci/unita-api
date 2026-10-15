package io.github.ronaldobertolucci.unita.repository;

import io.github.ronaldobertolucci.unita.model.investment.InvestmentTransaction;
import io.github.ronaldobertolucci.unita.model.investment.InvestmentTransactionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface InvestmentTransactionRepository extends JpaRepository<InvestmentTransaction, Long> {

    List<InvestmentTransaction> findAllByAssetIdOrderByTransactionDateDesc(Long assetId);

    Optional<InvestmentTransaction> findByIdAndAssetId(Long id, Long assetId);

    boolean existsByAssetId(Long assetId);

    Optional<InvestmentTransaction> findFirstByAssetIdAndTypeOrderByTransactionDateAsc(Long assetId, InvestmentTransactionType type);

    @Query("""
        SELECT DISTINCT it.transactionDate FROM InvestmentTransaction it
        WHERE it.asset.id = :assetId
          AND it.type = io.github.ronaldobertolucci.unita.model.investment.InvestmentTransactionType.BUY
        ORDER BY it.transactionDate
        """)
    List<LocalDate> findDistinctPurchaseDatesByAssetId(@Param("assetId") Long assetId);

    @Query("""
        SELECT COALESCE(SUM(it.amount), 0)
        FROM InvestmentTransaction it
        JOIN it.asset a
        WHERE a.user.id = :userId
          AND a.status = io.github.ronaldobertolucci.unita.model.investment.AssetStatus.REDEEMED
          AND it.type = io.github.ronaldobertolucci.unita.model.investment.InvestmentTransactionType.TAX
        """)
    BigDecimal sumTaxByUserIdAndRedeemedAssets(@Param("userId") Long userId);
}