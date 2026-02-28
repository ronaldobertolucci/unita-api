package io.github.ronaldobertolucci.unita.repository;

import io.github.ronaldobertolucci.unita.model.employer.Employer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EmployerRepository extends JpaRepository<Employer, Long> {

    List<Employer> findAllByUserId(Long userId);

    Optional<Employer> findByIdAndUserId(Long id, Long userId);

    boolean existsByIdAndUserId(Long id, Long userId);
}