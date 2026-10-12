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
    @Autowired io.github.ronaldobertolucci.unita.service.email.EmailVerificationService verification;
    @Autowired io.github.ronaldobertolucci.unita.service.user.UserService userService;
    @Autowired EmailVerificationTokenRepository verificationTokens;
    @Autowired org.springframework.transaction.support.TransactionTemplate transactions;
    @Autowired jakarta.persistence.EntityManager entityManager;
    @org.springframework.test.context.bean.override.mockito.MockitoBean
    io.github.ronaldobertolucci.unita.service.email.EmailService emailService;
    User user;

    @BeforeEach void setup() {
        jdbc.update("DELETE FROM refresh_sessions");
        // Avoid deleting unrelated seeded fixtures; use a new account for every test.
        user = users.save(User.builder().firstName("Auth").lastName("Test")
                .email(java.util.UUID.randomUUID() + "@example.com")
                .dateOfBirth(LocalDate.of(1990, 1, 1)).password(encoder.encode("password123"))
                .enabled(true).emailVerified(true).roles(Set.of(roles.findByName("USER").orElseThrow())).build());
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
    MvcResult attemptLogin(String email, String password) throws Exception {
        return mvc.perform(post("/auth/login").header("X-CSRF-Protection", "1")
                .contentType("application/json")
                .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andReturn();
    }
    void assertRejected(MvcResult result, int status, String code) throws Exception {
        assertEquals(status, result.getResponse().getStatus());
        String body = result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(body.contains("\"code\":\"" + code + "\""), body);
        assertNull(result.getResponse().getHeader("Set-Cookie"));
        assertNull(result.getRequest().getSession(false));
        assertFalse(body.contains("\"token\""));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM refresh_sessions", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM refresh_token_history", Integer.class));
    }
    @Test void passwordCorrectAccountStateMatrix() throws Exception {
        for (boolean enabled : new boolean[]{true, false}) {
            for (boolean verified : new boolean[]{true, false}) {
                if (enabled && verified) continue; // covered by loginCookieAndRotationThroughHttp
                jdbc.update("UPDATE users SET enabled = ?, email_verified = ? WHERE id = ?",
                        enabled, verified, user.getId());
                assertRejected(attemptLogin(user.getEmail(), "password123"), 403,
                        enabled ? "EMAIL_NOT_VERIFIED" : "ACCOUNT_DISABLED");
            }
        }
    }
    @Test void wrongPasswordAndUnknownUserHaveIdenticalGenericContract() throws Exception {
        for (boolean enabled : new boolean[]{true, false}) {
            for (boolean verified : new boolean[]{true, false}) {
                jdbc.update("UPDATE users SET enabled = ?, email_verified = ? WHERE id = ?",
                        enabled, verified, user.getId());
                var result = attemptLogin(user.getEmail(), "wrong-password");
                assertEquals(401, result.getResponse().getStatus());
                var body = result.getResponse().getContentAsString();
                assertTrue(body.contains("Invalid email or password"));
                assertFalse(body.contains("ACCOUNT_DISABLED"));
                assertFalse(body.contains("EMAIL_NOT_VERIFIED"));
                assertNull(result.getResponse().getHeader("Set-Cookie"));
                assertNull(result.getRequest().getSession(false));
            }
        }
        var unknown = attemptLogin("unknown-" + java.util.UUID.randomUUID() + "@example.com", "password123");
        assertEquals(401, unknown.getResponse().getStatus());
        assertTrue(unknown.getResponse().getContentAsString().contains("Invalid email or password"));
        assertNull(unknown.getResponse().getHeader("Set-Cookie"));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM refresh_sessions", Integer.class));
    }
    @Test void registrationAndAdministrativeEnableNeverConfirmEmail() throws Exception {
        String email = java.util.UUID.randomUUID() + "@example.com";
        mvc.perform(post("/auth/register").contentType("application/json")
                .content("{\"firstName\":\"Example\",\"lastName\":\"User\",\"email\":\"" + email
                        + "\",\"dateOfBirth\":\"1990-01-01\",\"password\":\"password123\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.emailVerified").value(false));
        User pending = users.findByEmailWithRoles(email).orElseThrow();
        assertTrue(pending.isEnabled()); assertFalse(pending.getEmailVerified());
        userService.disable(pending.getId());
        var enabled = userService.enable(pending.getId());
        assertTrue(enabled.enabled()); assertFalse(enabled.emailVerified());
        assertRejected(attemptLogin(email, "password123"), 403, "EMAIL_NOT_VERIFIED");
    }
    @Test void disablingBlocksLoginRefreshAndExistingJwt() throws Exception {
        var issued = sessions.login(user);
        userService.disable(user.getId());
        mvc.perform(get("/auth/me").header("Authorization", "Bearer " + issued.access().token()))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
        mvc.perform(post("/auth/login").header("X-CSRF-Protection", "1").contentType("application/json")
                .content("{\"email\":\"" + user.getEmail() + "\",\"password\":\"password123\"}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCOUNT_DISABLED"))
                .andExpect(header().doesNotExist("Set-Cookie"));
        mvc.perform(post("/auth/refresh").header("X-CSRF-Protection", "1")
                .cookie(new Cookie(RefreshCookie.NAME, issued.refreshToken())))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("SESSION_EXPIRED"));
    }
    @Test void unverifiedBlocksRefreshAndExistingJwt() throws Exception {
        var issued = sessions.login(user);
        jdbc.update("UPDATE users SET email_verified = false WHERE id = ?", user.getId());
        assertThrows(RefreshRejectedException.class, () -> sessions.refresh(issued.refreshToken()));
        mvc.perform(get("/auth/me").header("Authorization", "Bearer " + issued.access().token()))
                .andExpect(status().isUnauthorized());
    }
    @Test void staleAuthenticatedPrincipalCannotBypassDisable() {
        jdbc.update("UPDATE users SET enabled = false WHERE id = ?", user.getId());
        var ex = assertThrows(io.github.ronaldobertolucci.unita.exception.AccountStateException.class,
                () -> sessions.login(user));
        assertEquals("ACCOUNT_DISABLED", ex.getCode());
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM refresh_sessions", Integer.class));
    }
    @Test void resendPrivacyMatrixAndRealDeliveryFailure() throws Exception {
        org.mockito.Mockito.reset(emailService);
        for (boolean enabled : new boolean[]{true, false}) {
            for (boolean verified : new boolean[]{true, false}) {
                jdbc.update("UPDATE users SET enabled = ?, email_verified = ? WHERE id = ?", enabled, verified, user.getId());
                mvc.perform(post("/auth/resend-verification").contentType("application/json")
                        .content("{\"email\":\"" + user.getEmail() + "\"}"))
                        .andExpect(status().isOk()).andExpect(content().string(""));
            }
        }
        mvc.perform(post("/auth/resend-verification").contentType("application/json")
                .content("{\"email\":\"unknown-" + java.util.UUID.randomUUID() + "@example.com\"}"))
                .andExpect(status().isOk()).andExpect(content().string(""));
        org.mockito.Mockito.verify(emailService, org.mockito.Mockito.times(1)).sendHtmlEmailSynchronously(
                org.mockito.ArgumentMatchers.eq(user.getEmail()), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString());
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM email_verification_tokens WHERE user_id = ?", Integer.class, user.getId()));
        mvc.perform(post("/auth/resend-verification").contentType("application/json")
                .content("{\"email\":\"invalid\"}")).andExpect(status().isBadRequest());
        jdbc.update("UPDATE users SET enabled = true, email_verified = false WHERE id = ?", user.getId());
        org.mockito.Mockito.doThrow(new org.springframework.mail.MailSendException("delivery failure"))
                .when(emailService).sendHtmlEmailSynchronously(org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
        mvc.perform(post("/auth/resend-verification").contentType("application/json")
                .content("{\"email\":\"" + user.getEmail() + "\"}"))
                .andExpect(status().isInternalServerError()).andExpect(header().doesNotExist("Set-Cookie"));
        // Failure rolls back the newly created token.
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM email_verification_tokens WHERE user_id = ?", Integer.class, user.getId()));
    }
    @Test void confirmationWithStaleUserCannotOverwriteConcurrentDisable() throws Exception {
        jdbc.update("UPDATE users SET email_verified = false WHERE id = ?", user.getId());
        var token = verificationTokens.save(io.github.ronaldobertolucci.unita.model.security.EmailVerificationToken.builder()
                .token(java.util.UUID.randomUUID().toString()).user(user).expiryDate(LocalDateTime.now().plusHours(1)).build());
        try (var executor = Executors.newSingleThreadExecutor()) {
            transactions.executeWithoutResult(tx -> {
                User stale = users.findById(user.getId()).orElseThrow();
                assertTrue(stale.isEnabled());
                try { executor.submit(() -> userService.disable(user.getId())).get(15, TimeUnit.SECONDS); }
                catch (Exception e) { throw new RuntimeException(e); }
                // The token resolves to the already managed user with stale enabled=true.
                verification.verifyEmail(token.getToken());
            });
        }
        var confirmed = users.findById(user.getId()).orElseThrow();
        assertFalse(confirmed.isEnabled()); assertTrue(confirmed.getEmailVerified());
        assertTrue(verificationTokens.findByToken(token.getToken()).orElseThrow().isUsed());
        assertRejected(attemptLogin(user.getEmail(), "password123"), 403, "ACCOUNT_DISABLED");
    }
    @Test void administrativeUpdateDoesNotOverwriteConcurrentConfirmation() throws Exception {
        jdbc.update("UPDATE users SET enabled = false, email_verified = false WHERE id = ?", user.getId());
        var token = verificationTokens.save(io.github.ronaldobertolucci.unita.model.security.EmailVerificationToken.builder()
                .token(java.util.UUID.randomUUID().toString()).user(user).expiryDate(LocalDateTime.now().plusHours(1)).build());
        try (var executor = Executors.newSingleThreadExecutor()) {
            transactions.executeWithoutResult(tx -> {
                User stale = users.findById(user.getId()).orElseThrow();
                assertFalse(stale.getEmailVerified());
                try { executor.submit(() -> verification.verifyEmail(token.getToken())).get(15, TimeUnit.SECONDS); }
                catch (Exception e) { throw new RuntimeException(e); }
                userService.enable(user.getId());
            });
        }
        var enabled = users.findById(user.getId()).orElseThrow();
        assertTrue(enabled.isEnabled()); assertTrue(enabled.getEmailVerified());
    }

    @Test void confirmationCompletesPendingRegistrationAndAllowsLogin() throws Exception {
        jdbc.update("UPDATE users SET email_verified = false WHERE id = ?", user.getId());
        var token = verificationTokens.save(io.github.ronaldobertolucci.unita.model.security.EmailVerificationToken.builder()
                .token(java.util.UUID.randomUUID().toString()).user(user).expiryDate(LocalDateTime.now().plusHours(1)).build());
        mvc.perform(get("/auth/verify-email").param("token", token.getToken())).andExpect(status().isOk());
        var confirmed = users.findById(user.getId()).orElseThrow();
        assertTrue(confirmed.isEnabled()); assertTrue(confirmed.getEmailVerified());
        assertTrue(verificationTokens.findByToken(token.getToken()).orElseThrow().isUsed());
        login();
    }

}
