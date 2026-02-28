package io.github.ronaldobertolucci.unita.repository;

import io.github.ronaldobertolucci.unita.model.finance.LegalEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LegalEntityRepository extends JpaRepository<LegalEntity, Long> {

    List<LegalEntity> findAllByUserId(Long userId);

    Optional<LegalEntity> findByIdAndUserId(Long id, Long userId);

    boolean existsByIdAndUserId(Long id, Long userId);

    boolean existsByCnpjAndUserId(String cnpj, Long userId);

}