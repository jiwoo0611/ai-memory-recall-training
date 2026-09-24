package com.konkuk.memory.security;

import com.konkuk.memory.config.JwtProperties;
import com.konkuk.memory.dto.TokenResponse;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class TokenService {
    private final JwtEncoder encoder;
    private final JwtProperties properties;
    public TokenService(JwtEncoder encoder, JwtProperties properties) {
        this.encoder = encoder;
        this.properties = properties;
    }
    public TokenResponse issue(Long userId) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.issuer()).subject(userId.toString()).id(UUID.randomUUID().toString())
                .audience(List.of("memory-recall-api"))
                .issuedAt(now).expiresAt(now.plusSeconds(properties.ttlSeconds())).build();
        String token = encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
        return new TokenResponse(token, "Bearer", properties.ttlSeconds());
    }
}
