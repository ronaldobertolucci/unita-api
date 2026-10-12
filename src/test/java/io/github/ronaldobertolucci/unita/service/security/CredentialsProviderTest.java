package io.github.ronaldobertolucci.unita.service.security;

import io.github.ronaldobertolucci.unita.config.security.CredentialsConfiguration;
import io.github.ronaldobertolucci.unita.exception.AccountStateException;
import io.github.ronaldobertolucci.unita.model.user.User;
import io.github.ronaldobertolucci.unita.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CredentialsProviderTest {
    @Test void databaseFailureRemainsInternalAuthenticationFailure() {
        var users = mock(UserRepository.class);
        when(users.findByEmailWithRoles("user@example.com"))
                .thenThrow(new DataAccessResourceFailureException("unavailable"));
        var provider = new CredentialsConfiguration().credentialsProvider(
                new AuthenticationService(users), new BCryptPasswordEncoder());
        assertThrows(InternalAuthenticationServiceException.class, () -> provider.authenticate(
                new UsernamePasswordAuthenticationToken("user@example.com", "password123")));
    }
    @Test void disabledHasPriorityOverMissingVerificationAfterPasswordCheck() {
        var users = mock(UserRepository.class);
        var encoder = new BCryptPasswordEncoder();
        when(users.findByEmailWithRoles("user@example.com")).thenReturn(Optional.of(User.builder()
                .email("user@example.com").password(encoder.encode("password123"))
                .enabled(false).emailVerified(false).build()));
        var provider = new CredentialsConfiguration().credentialsProvider(new AuthenticationService(users), encoder);
        var failure = assertThrows(AccountStateException.class, () -> provider.authenticate(
                new UsernamePasswordAuthenticationToken("user@example.com", "password123")));
        assertEquals("ACCOUNT_DISABLED", failure.getCode());
        assertThrows(org.springframework.security.authentication.BadCredentialsException.class, () -> provider.authenticate(
                new UsernamePasswordAuthenticationToken("user@example.com", "wrong-password")));
    }
}
