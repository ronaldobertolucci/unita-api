package io.github.ronaldobertolucci.unita.repository;

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
}