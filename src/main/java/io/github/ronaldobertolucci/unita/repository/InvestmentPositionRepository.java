package io.github.ronaldobertolucci.unita.repository;

import io.github.ronaldobertolucci.unita.model.investment.InvestmentPosition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface InvestmentPositionRepository extends JpaRepository<InvestmentPosition, Long> {

    Optional<InvestmentPosition> findByAssetId(Long assetId);
}