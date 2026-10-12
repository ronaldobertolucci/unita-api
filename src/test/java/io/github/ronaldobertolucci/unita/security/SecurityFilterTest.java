package io.github.ronaldobertolucci.unita.security;

import com.auth0.jwt.exceptions.JWTVerificationException;
import io.github.ronaldobertolucci.unita.config.security.SecurityFilter;
import io.github.ronaldobertolucci.unita.repository.UserRepository;
import io.github.ronaldobertolucci.unita.service.security.TokenService;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SecurityFilterTest {
    @AfterEach void cleanup() { SecurityContextHolder.clearContext(); }
    @Test void invalidTokenClearsResidualAuthentication() throws Exception {
        var tokens = mock(TokenService.class);
        var users = mock(UserRepository.class);
        var chain = mock(FilterChain.class);
        when(tokens.getSubject("invalid")).thenThrow(new JWTVerificationException("invalid"));
        var request = new MockHttpServletRequest("GET", "/auth/me");
        request.addHeader("Authorization", "Bearer invalid");
        var response = new MockHttpServletResponse();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("residual", null, List.of()));
        new SecurityFilter(tokens, users).doFilter(request, response, chain);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        assertEquals(401, response.getStatus());
        assertEquals("Bearer", response.getHeader("WWW-Authenticate"));
        verifyNoInteractions(chain, users);
    }
    @Test void databaseFailurePropagatesInsteadOfBecomingUnauthorized() {
        var tokens = mock(TokenService.class);
        var users = mock(UserRepository.class);
        when(tokens.getSubject("valid")).thenReturn("user@example.com");
        when(users.findByEmailWithRoles("user@example.com"))
                .thenThrow(new DataAccessResourceFailureException("database unavailable"));
        var request = new MockHttpServletRequest("GET", "/auth/me");
        request.addHeader("Authorization", "Bearer valid");
        var response = new MockHttpServletResponse();
        assertThrows(DataAccessResourceFailureException.class,
                () -> new SecurityFilter(tokens, users).doFilter(request, response, mock(FilterChain.class)));
        assertNotEquals(401, response.getStatus());
    }
}
