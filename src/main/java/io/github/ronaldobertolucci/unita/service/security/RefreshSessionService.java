package io.github.ronaldobertolucci.unita.service.security;

import io.github.ronaldobertolucci.unita.dto.security.TokenDto;
import io.github.ronaldobertolucci.unita.dto.user.UserDto;
import io.github.ronaldobertolucci.unita.exception.RefreshRejectedException;
import io.github.ronaldobertolucci.unita.exception.AccountStateException;
import jakarta.persistence.EntityManager;
import io.github.ronaldobertolucci.unita.model.user.User;
import io.github.ronaldobertolucci.unita.repository.RefreshSessionRepository;
import io.github.ronaldobertolucci.unita.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RefreshSessionService {
    private final RefreshSessionRepository sessions;
    private final UserRepository users;
    private final TokenService tokens;
    private final EntityManager entityManager;
    private static final SecureRandom RANDOM = new SecureRandom();
    @Value("${api.security.refresh.expiration-days:7}") private int expirationDays;

    public record Issued(TokenDto access, String refreshToken, Instant expiresAt) {
        @Override public String toString() { return "Issued[redacted]"; }
    }
    @Transactional
    public Issued login(User authenticated) {
        if (!sessions.lockUser(authenticated.getId())) throw new BadCredentialsException("Invalid credentials");
        User current = users.findByIdWithRoles(authenticated.getId()).orElseThrow(RefreshRejectedException::new);
        if (!sessions.credentialsStillValid(authenticated.getId(), authenticated.getPassword()))
            throw new BadCredentialsException("Invalid credentials");
        // OSIV may have cached this entity before the lock; reload administrative state.
        entityManager.refresh(current);
        AccountStateException.check(current);
        if (expirationDays <= 0) throw new IllegalStateException("Refresh expiration must be positive");
        String refresh = randomToken();
        Instant expiresAt = Instant.now().plus(Duration.ofDays(expirationDays)).truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        TokenDto access = access(current);
        sessions.create(UUID.randomUUID(), current.getId(), expiresAt, hash(refresh));
        return new Issued(access, refresh, expiresAt);
    }
    @Transactional(noRollbackFor = RefreshRejectedException.class)
    public Issued refresh(String refresh) {
        if (!validFormat(refresh)) throw new RefreshRejectedException();
        String hash = hash(refresh);
        var owner = sessions.findOwner(hash).orElseThrow(RefreshRejectedException::new);
        if (!sessions.lockUser(owner.userId())) throw new RefreshRejectedException();
        var session = sessions.lockSession(owner.sessionId()).orElseThrow(RefreshRejectedException::new);
        if (session.revoked() || !session.expiresAt().isAfter(Instant.now())) throw new RefreshRejectedException();
        User user = users.findByIdWithRoles(owner.userId()).orElseThrow(RefreshRejectedException::new);
        entityManager.refresh(user);
        if (!session.currentHash().equals(hash) || !user.isAccessAllowed()) {
            sessions.revoke(session.id());
            throw new RefreshRejectedException();
        }
        String next = randomToken();
        TokenDto access = access(user);
        sessions.rotate(session.id(), hash(next));
        return new Issued(access, next, session.expiresAt());
    }
    @Transactional
    public void logout(String refresh) {
        if (!validFormat(refresh)) return;
        sessions.findOwner(hash(refresh)).ifPresent(owner -> {
            if (sessions.lockUser(owner.userId()))
                sessions.lockSession(owner.sessionId()).ifPresent(session -> sessions.revoke(session.id()));
        });
    }
    @Scheduled(cron = "${api.security.refresh.cleanup.cron:0 30 2 * * ?}")
    @Transactional
    public void purgeExpiredSessions() { sessions.deleteExpired(Instant.now()); }

    private TokenDto access(User user) {
        return new TokenDto(tokens.generateToken(user), tokens.expiresInSeconds(), new UserDto(user));
    }
    private static String randomToken() {
        byte[] bytes = new byte[32]; RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
    static boolean validFormat(String token) {
        if (token == null || !token.matches("[A-Za-z0-9_-]{43}")) return false;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(Base64.getUrlDecoder().decode(token)).equals(token);
    }
    public static String hash(String token) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException("SHA-256 unavailable", e); }
    }
}
