package io.github.ronaldobertolucci.unita.repository;

import io.github.ronaldobertolucci.unita.model.investment.InvestmentSaleOperation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface InvestmentSaleOperationRepository extends JpaRepository<InvestmentSaleOperation, Long> {
    Optional<InvestmentSaleOperation> findByUserIdAndOperationId(Long userId, UUID operationId);
}
