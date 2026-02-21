package io.github.ronaldobertolucci.unita.repository;

import io.github.ronaldobertolucci.unita.model.user.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

import java.time.LocalDate;
import java.util.Set;

/**
 * Base class for all financial domain repository tests.
 * Extends the existing BaseRepositoryTest pattern:
 * - Uses H2 with PostgreSQL mode via application-test.properties
 * - Flyway runs all migrations (V1-V15) including seeds for reference data
 * - Does NOT re-insert roles (already seeded by Flyway)
 * - Provides saveUser() helper for tests that require a persisted User
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
public abstract class BaseRepositoryTest {

    @Autowired
    protected UserRepository userRepository;

    @Autowired
    protected RoleRepository roleRepository;

    protected User saveUser(String email) {
        var userRole = roleRepository.findByName("USER")
                .orElseThrow(() -> new RuntimeException("USER role not found — check Flyway seeds"));

        User user = User.builder()
                .firstName("Test")
                .lastName("User")
                .email(email)
                .dateOfBirth(LocalDate.of(1990, 1, 1))
                .password("encoded-password")
                .enabled(true)
                .roles(Set.of(userRole))
                .build();

        return userRepository.save(user);
    }
}