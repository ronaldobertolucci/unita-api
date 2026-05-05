package io.github.ronaldobertolucci.unita.repository;

import io.github.ronaldobertolucci.unita.model.security.EmailVerificationToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface EmailVerificationTokenRepository extends JpaRepository<EmailVerificationToken, Long> {

    Optional<EmailVerificationToken> findByToken(String token);

    boolean existsByUserIdAndUsedFalse(Long userId);
}