package com.konkuk.memory.dto;

public record TokenResponse(String accessToken, String tokenType, long expiresIn) {
    @Override public String toString() { return "TokenResponse[REDACTED]"; }
}
