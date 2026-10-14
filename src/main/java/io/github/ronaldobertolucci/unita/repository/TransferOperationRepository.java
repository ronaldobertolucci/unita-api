package io.github.ronaldobertolucci.unita.repository;

import io.github.ronaldobertolucci.unita.model.pocket.TransferOperation;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransferOperationRepository extends JpaRepository<TransferOperation, Long> {}
