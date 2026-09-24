package com.konkuk.memory.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.sql.Timestamp;
import java.time.Instant;

/** Independent security records; no relationship to domain entities. */
@Repository
public class RevokedTokenRepository {
    private final JdbcTemplate jdbc;

    public RevokedTokenRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public void save(String jti, Instant expiresAt) {
        // Concurrent logout requests for the same signed token are safe.
        jdbc.update("INSERT INTO revoked_tokens (jti, expires_at) VALUES (?, ?) "
                + "ON DUPLICATE KEY UPDATE expires_at = VALUES(expires_at)", jti, Timestamp.from(expiresAt));
    }

    public boolean exists(String jti) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT COUNT(*) > 0 FROM revoked_tokens WHERE jti = ?", Boolean.class, jti));
    }

    public int deleteExpired(Instant now) {
        return jdbc.update("DELETE FROM revoked_tokens WHERE expires_at < ?", Timestamp.from(now));
    }
}
