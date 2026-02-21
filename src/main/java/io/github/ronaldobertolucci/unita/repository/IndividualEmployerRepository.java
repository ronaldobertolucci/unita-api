package io.github.ronaldobertolucci.unita.repository;

import io.github.ronaldobertolucci.unita.model.employer.IndividualEmployer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface IndividualEmployerRepository extends JpaRepository<IndividualEmployer, Long> {

    boolean existsByCpf(String cpf);
}