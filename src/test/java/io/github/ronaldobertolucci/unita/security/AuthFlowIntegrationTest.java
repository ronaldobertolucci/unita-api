package io.github.ronaldobertolucci.unita.security;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import io.github.ronaldobertolucci.unita.config.security.RefreshCookie;
import io.github.ronaldobertolucci.unita.exception.RefreshRejectedException;
import io.github.ronaldobertolucci.unita.model.security.PasswordResetToken;
import io.github.ronaldobertolucci.unita.model.user.User;
import io.github.ronaldobertolucci.unita.repository.*;
import io.github.ronaldobertolucci.unita.service.security.*;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import java.time.*;
import java.sql.Timestamp;
import java.util.Set;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthFlowIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired RefreshSessionRepository repository;
    @Autowired RefreshSessionService sessions;
    @Autowired TokenService tokens;
    @Autowired PasswordEncoder encoder;
    @Autowired PasswordResetService passwords;
    @Autowired PasswordResetTokenRepository resetTokens;
    @Autowired JdbcTemplate jdbc;
    User user;

    @BeforeEach void setup() {
        jdbc.update("DELETE FROM refresh_sessions");
        // Avoid deleting unrelated seeded fixtures; use a new account for every test.
        user = users.save(User.builder().firstName("Auth").lastName("Test")
                .email(java.util.UUID.randomUUID() + "@example.com")
                .dateOfBirth(LocalDate.of(1990, 1, 1)).password(encoder.encode("password123"))
                .enabled(true).roles(Set.of(roles.findByName("USER").orElseThrow())).build());
    }
    String expiredJwt() {
        return JWT.create().withIssuer("Unita API").withSubject(user.getEmail())
                .withExpiresAt(Instant.now().minusSeconds(30)).sign(Algorithm.HMAC256("test-secret-key"));
    }
    @Test void protectedResourceErrorsAndPermission() throws Exception {
        mvc.perform(get("/auth/me")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"))
                .andExpect(header().string("WWW-Authenticate", "Bearer"));
        mvc.perform(get("/auth/me").header("Authorization", "Bearer nonsense"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
        mvc.perform(get("/auth/me").header("Authorization", "Bearer " + expiredJwt()))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("SESSION_EXPIRED"));
        mvc.perform(get("/admin/anything").header("Authorization", "Bearer " + tokens.generateToken(user)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        mvc.perform(get("/auth/me").header("Authorization", "Bearer " + tokens.generateToken(user)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(user.getId()));
    }
    MvcResult login() throws Exception {
        return mvc.perform(post("/api/auth/login").contextPath("/api")
                        .header("X-CSRF-Protection", "1").header("Authorization", "Bearer " + expiredJwt())
                        .contentType("application/json")
                        .content("{\"email\":\"" + user.getEmail() + "\",\"password\":\"password123\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.expiresIn").value(7200))
                .andExpect(jsonPath("$.type").value("Bearer"))
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andExpect(header().string("Cache-Control", "no-store")).andReturn();
    }
    String cookieToken(MvcResult result) {
        String cookie = result.getResponse().getHeader("Set-Cookie");
        assertNotNull(cookie);
        return cookie.substring(cookie.indexOf('=') + 1, cookie.indexOf(';'));
    }
    @Test void loginCookieAndRotationThroughHttp() throws Exception {
        var first = login();
        String cookie = first.getResponse().getHeader("Set-Cookie");
        assertTrue(cookie.contains("HttpOnly")); assertTrue(cookie.contains("Path=/api/auth"));
        assertTrue(cookie.contains("SameSite=Lax")); assertFalse(cookie.contains("Secure"));
        assertFalse(cookie.contains("Domain="));
        String old = cookieToken(first);
        var next = mvc.perform(post("/api/auth/refresh").contextPath("/api")
                        .header("X-CSRF-Protection", "1").cookie(new Cookie(RefreshCookie.NAME, old)))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store")).andReturn();
        String current = cookieToken(next); assertNotEquals(old, current);
        mvc.perform(post("/api/auth/refresh").contextPath("/api")
                        .header("X-CSRF-Protection", "1").cookie(new Cookie(RefreshCookie.NAME, old)))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("SESSION_EXPIRED"))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("WWW-Authenticate", "Bearer"))
                .andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.containsString("Max-Age=0")));
        assertThrows(RefreshRejectedException.class, () -> sessions.refresh(current));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM refresh_sessions WHERE revoked = true", Integer.class));
    }
    @Test void csrfAndCors() throws Exception {
        for (String endpoint : new String[]{"login", "refresh", "logout"})
            mvc.perform(post("/auth/" + endpoint)).andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("CSRF_PROTECTION_REQUIRED"));
        mvc.perform(post("/auth/refresh").header("Origin", "http://evil.example")
                        .header("X-CSRF-Protection", "1")).andExpect(status().isForbidden());
        mvc.perform(options("/auth/refresh").header("Origin", "http://localhost:4200")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "X-CSRF-Protection,Content-Type"))
                .andExpect(status().isOk()).andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:4200"))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
        mvc.perform(post("/auth/refresh").header("X-CSRF-Protection", "1"))
                .andExpect(status().isUnauthorized()).andExpect(header().string("Cache-Control", "no-store"));
        mvc.perform(post("/auth/logout").header("X-CSRF-Protection", "1"))
                .andExpect(status().isNoContent()).andExpect(header().string("Cache-Control", "no-store"));
        mvc.perform(post("/auth/logout").header("X-CSRF-Protection", "1").cookie(new Cookie(RefreshCookie.NAME, "bad")))
                .andExpect(status().isNoContent());
    }
    @Test void absoluteExpiryHashesReplayAndLogoutIsolation() {
        var first = sessions.login(user); var other = sessions.login(user);
        assertEquals(43, first.refreshToken().length());
        var rotated = sessions.refresh(first.refreshToken());
        assertEquals(first.expiresAt(), rotated.expiresAt());
        assertEquals(3, jdbc.queryForObject("SELECT count(*) FROM refresh_token_history", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM refresh_token_history WHERE token_hash = ?",
                Integer.class, first.refreshToken()));
        sessions.logout(rotated.refreshToken()); sessions.logout(rotated.refreshToken());
        assertThrows(RefreshRejectedException.class, () -> sessions.refresh(rotated.refreshToken()));
        assertNotNull(sessions.refresh(other.refreshToken()));
    }
    @Test void expiredRevokedDisabledAndCleanup() {
        var expired = sessions.login(user); var valid = sessions.login(user);
        var owner = repository.findOwner(RefreshSessionService.hash(expired.refreshToken())).orElseThrow();
        jdbc.update("UPDATE refresh_sessions SET expires_at = ? WHERE id = ?", Timestamp.from(Instant.now().minusSeconds(1)), owner.sessionId());
        assertThrows(RefreshRejectedException.class, () -> sessions.refresh(expired.refreshToken()));
        sessions.purgeExpiredSessions();
        assertTrue(repository.findOwner(RefreshSessionService.hash(expired.refreshToken())).isEmpty());
        assertTrue(repository.findOwner(RefreshSessionService.hash(valid.refreshToken())).isPresent());
        user.setEnabled(false); users.save(user);
        assertThrows(RefreshRejectedException.class, () -> sessions.refresh(valid.refreshToken()));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM refresh_sessions WHERE revoked = true", Integer.class));
        assertThrows(RefreshRejectedException.class, () -> sessions.refresh(null));
        assertThrows(RefreshRejectedException.class, () -> sessions.refresh("invalid"));
        assertThrows(RefreshRejectedException.class, () -> sessions.refresh("A".repeat(43)));
    }
    @Test void passwordResetRevokesEverySessionButJwtRemainsValid() {
        var first = sessions.login(user); var second = sessions.login(user);
        var token = resetTokens.save(new PasswordResetToken(java.util.UUID.randomUUID().toString(), user, 1));
        passwords.resetPassword(token.getToken(), "new-password123");
        assertThrows(RefreshRejectedException.class, () -> sessions.refresh(first.refreshToken()));
        assertThrows(RefreshRejectedException.class, () -> sessions.refresh(second.refreshToken()));
        assertEquals(user.getEmail(), tokens.getSubject(first.access().token()));
        assertTrue(encoder.matches("new-password123", users.findById(user.getId()).orElseThrow().getPassword()));
    }
    @Test void staleLoginPrincipalCannotCreateSessionAfterPasswordReset() {
        User stale = user;
        jdbc.update("UPDATE users SET password = ? WHERE id = ?", encoder.encode("changed-password"), user.getId());
        assertThrows(org.springframework.security.authentication.BadCredentialsException.class, () -> sessions.login(stale));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM refresh_sessions", Integer.class));
    }
    @Test void csrfCannotBeBypassedByEncodedPath() throws Exception {
        // MockMvc resolves the decoded path; the interceptor checks its handler, not raw URI strings.
        mvc.perform(post(java.net.URI.create("/auth/%72efresh"))).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_PROTECTION_REQUIRED"));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM refresh_sessions", Integer.class));
    }
    @Test void concurrentRefreshCommitsReplayRevocation() throws Exception {
        var first = sessions.login(user);
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Callable<String> attempt = () -> {
                start.await();
                try { return sessions.refresh(first.refreshToken()).refreshToken(); }
                catch (RefreshRejectedException e) { return null; }
            };
            var a = executor.submit(attempt); var b = executor.submit(attempt); start.countDown();
            String one = a.get(15, TimeUnit.SECONDS), two = b.get(15, TimeUnit.SECONDS);
            assertTrue((one == null) != (two == null), "Exactly one rotation should succeed");
            assertThrows(RefreshRejectedException.class, () -> sessions.refresh(one == null ? two : one));
            assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM refresh_sessions WHERE revoked = true", Integer.class));
        }
    }
}
