package io.github.ronaldobertolucci.unita.repository;

import io.github.ronaldobertolucci.unita.model.finance.LegalEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LegalEntityRepository extends JpaRepository<LegalEntity, Long> {

    List<LegalEntity> findAllByUserIdOrderByCorporateName(Long userId);

    Optional<LegalEntity> findByIdAndUserId(Long id, Long userId);

    boolean existsByIdAndUserId(Long id, Long userId);

    // Historical text remains unchanged, but cannot be registered again under its canonical spelling.
    @Query("""
            SELECT COUNT(e) > 0 FROM LegalEntity e WHERE e.user.id = :userId
              AND upper(replace(replace(replace(e.cnpj, '.', ''), '/', ''), '-', '')) = :cnpj
            """)
    boolean existsByCnpjAndUserId(String cnpj, Long userId);

}