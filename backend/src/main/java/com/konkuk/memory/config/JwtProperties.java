package com.konkuk.memory.config;

import jakarta.validation.constraints.*;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(@NotBlank String secret, @NotBlank String issuer,
                            @Min(60) @Max(3600) long ttlSeconds) {
    @Override public String toString() { return "JwtProperties[REDACTED]"; }
}
