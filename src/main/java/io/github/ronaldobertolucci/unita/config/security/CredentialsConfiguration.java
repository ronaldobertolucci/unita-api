package io.github.ronaldobertolucci.unita.config.security;

import io.github.ronaldobertolucci.unita.exception.AccountStateException;
import io.github.ronaldobertolucci.unita.model.user.User;
import io.github.ronaldobertolucci.unita.service.security.AuthenticationService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class CredentialsConfiguration {
    @Bean
    public DaoAuthenticationProvider credentialsProvider(AuthenticationService users, PasswordEncoder encoder) {
        var provider = new DaoAuthenticationProvider(users);
        provider.setPasswordEncoder(encoder);
        // Keep password checks and unknown-user timing protection. The default pre-check
        // reveals disabled state before checking the password, so move state checks after it.
        provider.setPreAuthenticationChecks(user -> {});
        provider.setPostAuthenticationChecks(user -> AccountStateException.check((User) user));
        return provider;
    }
}
