package io.github.ronaldobertolucci.unita.repository;

import io.github.ronaldobertolucci.unita.model.employer.IndividualEmployer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface IndividualEmployerRepository extends JpaRepository<IndividualEmployer, Long> {

    boolean existsByCpf(String cpf);

    List<IndividualEmployer> findAllByUserId(Long userId);

    Optional<IndividualEmployer> findByIdAndUserId(Long id, Long userId);

    boolean existsByIdAndUserId(Long id, Long userId);

    boolean existsByCpfAndUserId(String cpf, Long userId);

    @Query("""
            SELECT COUNT(f) > 0 FROM FgtsEmployerAccount f
            WHERE f.employer.id = :employerId
            """)
    boolean existsFgtsAccountByEmployerId(@Param("employerId") Long employerId);
}