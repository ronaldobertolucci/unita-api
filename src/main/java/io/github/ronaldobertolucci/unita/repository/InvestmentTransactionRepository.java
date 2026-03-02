package io.github.ronaldobertolucci.unita.repository;

import io.github.ronaldobertolucci.unita.model.investment.InvestmentTransaction;
import io.github.ronaldobertolucci.unita.model.investment.InvestmentTransactionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface InvestmentTransactionRepository extends JpaRepository<InvestmentTransaction, Long> {

    List<InvestmentTransaction> findAllByAssetIdOrderByTransactionDateDesc(Long assetId);

    Optional<InvestmentTransaction> findByIdAndAssetId(Long id, Long assetId);

    boolean existsByAssetId(Long assetId);

    Optional<InvestmentTransaction> findFirstByAssetIdAndTypeOrderByTransactionDateAsc(Long assetId, InvestmentTransactionType type);
}