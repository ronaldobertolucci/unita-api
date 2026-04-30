package io.github.ronaldobertolucci.unita.repository;

import io.github.ronaldobertolucci.unita.dto.dashboard.IndexerSummaryDto;
import io.github.ronaldobertolucci.unita.dto.dashboard.IssuerRiskSummaryDto;
import io.github.ronaldobertolucci.unita.model.investment.Asset;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AssetRepository extends JpaRepository<Asset, Long> {

    List<Asset> findAllByUserId(Long userId);

    Optional<Asset> findByIdAndUserId(Long id, Long userId);

    boolean existsByIdAndUserId(Long id, Long userId);

    boolean existsByNameAndUserId(String name, Long userId);

    @Query("SELECT COUNT(a) > 0 FROM Asset a WHERE a.id = :assetId AND a.user.id = :userId AND a.status = 'ACTIVE'")
    boolean existsActiveByIdAndUserId(@Param("assetId") Long assetId, @Param("userId") Long userId);

    @Query("""
            SELECT a FROM Asset a
            LEFT JOIN FETCH a.position
            LEFT JOIN FETCH a.fixedIncomeDetails
            LEFT JOIN FETCH a.pensionDetails
            LEFT JOIN FETCH a.legalEntity
            WHERE a.user.id = :userId
            """)
    List<Asset> findAllByUserIdWithDetails(@Param("userId") Long userId);

    @Query("""
        SELECT new io.github.ronaldobertolucci.unita.dto.dashboard.IssuerRiskSummaryDto(
            a.legalEntity.corporateName,
            SUM(p.currentValue)
        )
        FROM Asset a
        JOIN a.position p
        WHERE a.user.id = :userId
          AND a.status <> io.github.ronaldobertolucci.unita.model.investment.AssetStatus.REDEEMED
        GROUP BY a.legalEntity.corporateName
        ORDER BY a.legalEntity.corporateName
        """)
    List<IssuerRiskSummaryDto> sumCurrentValueByLegalEntityAndUserId(@Param("userId") Long userId);

    @Query("""
        SELECT new io.github.ronaldobertolucci.unita.dto.dashboard.IndexerSummaryDto(
            f.indexer,
            SUM(p.currentValue)
        )
        FROM Asset a
        JOIN a.position p
        JOIN a.fixedIncomeDetails f
        WHERE a.user.id = :userId
          AND a.status <> io.github.ronaldobertolucci.unita.model.investment.AssetStatus.REDEEMED
        GROUP BY f.indexer
        ORDER BY f.indexer
        """)
    List<IndexerSummaryDto> sumCurrentValueByIndexerAndUserId(@Param("userId") Long userId);
}