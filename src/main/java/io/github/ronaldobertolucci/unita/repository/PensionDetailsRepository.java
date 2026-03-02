package io.github.ronaldobertolucci.unita.repository;

import io.github.ronaldobertolucci.unita.model.investment.PensionDetails;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PensionDetailsRepository extends JpaRepository<PensionDetails, Long> {

    Optional<PensionDetails> findByAssetId(Long assetId);
}