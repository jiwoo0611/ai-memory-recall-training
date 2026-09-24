package com.konkuk.memory.dto;

import com.konkuk.memory.entity.User;
import java.time.Instant;

public record UserProfileResponse(Long id, String name, String email, Instant createdAt, Instant updatedAt) {
    public static UserProfileResponse from(User user) {
        return new UserProfileResponse(user.getId(), user.getName(), user.getEmail(), user.getCreatedAt(), user.getUpdatedAt());
    }
}
