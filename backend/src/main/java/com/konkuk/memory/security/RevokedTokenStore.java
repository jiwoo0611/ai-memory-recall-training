package com.konkuk.memory.security;

import org.springframework.stereotype.Component;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import com.konkuk.memory.repository.RevokedTokenRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;

/** Database-backed revocations shared by every server using the same database. */
@Component
public class RevokedTokenStore {
    private final RevokedTokenRepository revoked;

    public RevokedTokenStore(RevokedTokenRepository revoked) { this.revoked = revoked; }

    @Transactional
    public void revoke(Jwt verifiedToken) {
        removeExpired();
        revoked.save(identifier(verifiedToken), verifiedToken.getExpiresAt());
    }

    @Transactional(readOnly = true)
    public boolean isRevoked(Jwt verifiedToken) {
        return revoked.exists(identifier(verifiedToken));
    }

    @Transactional
    public int removeExpired() {
        return revoked.deleteExpired(Instant.now());
    }

    private String identifier(Jwt jwt) {
        // Existing pre-jti tokens remain logout-capable without storing the raw token.
        return jwt.getId() == null || jwt.getId().isBlank()
                ? "legacy:" + fingerprint(jwt.getTokenValue()) : jwt.getId();
    }

    private String fingerprint(String token) {
        // Hash the signed header/payload, so alternate signature encodings cannot bypass revocation.
        String signedContent = token.substring(0, token.lastIndexOf('.'));
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(signedContent.getBytes(StandardCharsets.US_ASCII)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is required", ex);
        }
    }
}
