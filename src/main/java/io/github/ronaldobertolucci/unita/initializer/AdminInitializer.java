package io.github.ronaldobertolucci.unita.initializer;

import io.github.ronaldobertolucci.unita.model.security.Role;
import io.github.ronaldobertolucci.unita.model.user.User;
import io.github.ronaldobertolucci.unita.repository.RoleRepository;
import io.github.ronaldobertolucci.unita.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.DependsOn;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

@Component
@DependsOn("flyway")
@RequiredArgsConstructor
public class AdminInitializer implements ApplicationRunner {

    private static final Logger logger = LoggerFactory.getLogger(AdminInitializer.class);
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    @Value("${admin.initialization.enabled:false}")
    private boolean adminInitEnabled;
    @Value("${admin.email}")
    private String adminEmail;
    @Value("${admin.password}")
    private String adminPassword;
    @Value("${admin.firstName}")
    private String adminFirstName;
    @Value("${admin.lastName}")
    private String adminLastName;

    @Override
    @Transactional
    public void run(@NonNull ApplicationArguments args) {
        if (!adminInitEnabled) {
            logger.info("Admin initialization disabled");
            return;
        }

        if (userRepository.existsByEmail(adminEmail)) {
            logger.info("Admin user already exists");
            return;
        }

        Role adminRole = roleRepository.findByName("ADMIN")
                .orElseThrow(() -> new RuntimeException("ADMIN role not found in database"));

        User admin = User.builder()
                .firstName(adminFirstName)
                .lastName(adminLastName)
                .email(adminEmail)
                .password(passwordEncoder.encode(adminPassword))
                .dateOfBirth(LocalDate.of(1990, 1, 1))
                .enabled(true)
                .roles(new HashSet<>(Set.of(adminRole)))
                .build();

        userRepository.save(admin);

        logger.warn("═══════════════════════════════════════");
        logger.warn("Admin user created:");
        logger.warn("Email: {}", adminEmail);
        logger.warn("Password: {}", adminPassword);
        logger.warn("⚠️  CHANGE THIS PASSWORD IMMEDIATELY!");
        logger.warn("═══════════════════════════════════════");
    }
}