package io.github.ronaldobertolucci.unita.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class RefreshSessionRepository {
    private final JdbcTemplate jdbc;
    public record TokenOwner(UUID sessionId, long userId) {}
    public record Session(UUID id, long userId, Instant expiresAt, boolean revoked, String currentHash) {}

    // Every account operation locks user first, then session, including password reset.
    public boolean lockUser(long userId) {
        return !jdbc.query("SELECT id FROM users WHERE id = ? FOR UPDATE",
                (rs, row) -> rs.getLong(1), userId).isEmpty();
    }
    public boolean credentialsStillValid(long userId, String passwordHash) {
        // Read directly after the lock: OSIV can contain an authentication principal loaded before a password reset.
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT enabled AND password = ? FROM users WHERE id = ?", Boolean.class, passwordHash, userId));
    }
    public Optional<TokenOwner> findOwner(String hash) {
        return jdbc.query("""
                SELECT s.id, s.user_id FROM refresh_token_history h
                JOIN refresh_sessions s ON s.id = h.session_id WHERE h.token_hash = ?
                """, (rs, row) -> new TokenOwner(rs.getObject(1, UUID.class), rs.getLong(2)), hash)
                .stream().findFirst();
    }
    public Optional<Session> lockSession(UUID id) {
        return jdbc.query("SELECT * FROM refresh_sessions WHERE id = ? FOR UPDATE",
                (rs, row) -> new Session(rs.getObject("id", UUID.class), rs.getLong("user_id"),
                        rs.getTimestamp("expires_at").toInstant(), rs.getBoolean("revoked"),
                        rs.getString("current_hash")), id).stream().findFirst();
    }
    public void create(UUID id, long userId, Instant expiresAt, String hash) {
        jdbc.update("INSERT INTO refresh_sessions(id, user_id, expires_at, revoked, current_hash) VALUES (?, ?, ?, false, ?)",
                id, userId, Timestamp.from(expiresAt), hash);
        remember(id, hash);
    }
    public void rotate(UUID id, String hash) {
        jdbc.update("UPDATE refresh_sessions SET current_hash = ? WHERE id = ?", hash, id);
        remember(id, hash);
    }
    private void remember(UUID id, String hash) {
        jdbc.update("INSERT INTO refresh_token_history(token_hash, session_id) VALUES (?, ?)", hash, id);
    }
    public void revoke(UUID id) { jdbc.update("UPDATE refresh_sessions SET revoked = true WHERE id = ?", id); }
    public void revokeAll(long userId) { jdbc.update("UPDATE refresh_sessions SET revoked = true WHERE user_id = ?", userId); }
    public int deleteExpired(Instant now) {
        return jdbc.update("DELETE FROM refresh_sessions WHERE expires_at <= ?", Timestamp.from(now));
    }
}
