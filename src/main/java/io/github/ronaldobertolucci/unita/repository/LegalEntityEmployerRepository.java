package io.github.ronaldobertolucci.unita.repository;

import io.github.ronaldobertolucci.unita.model.employer.LegalEntityEmployer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LegalEntityEmployerRepository extends JpaRepository<LegalEntityEmployer, Long> {

    boolean existsByLegalEntityId(Long legalEntityId);
}