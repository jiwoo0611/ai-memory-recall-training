package com.konkuk.memory.dto;

import jakarta.validation.constraints.*;
import java.util.Locale;

public record SignupRequest(
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Size(min = 8, max = 72) String password,
        @NotBlank @Size(max = 100) String name) {
    public SignupRequest {
        email = email == null ? null : email.strip().toLowerCase(Locale.ROOT);
        name = name == null ? null : name.strip();
    }
    @Override public String toString() { return "SignupRequest[REDACTED]"; }
}
