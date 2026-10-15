package com.glitchbytez.splitwise.user;

import java.time.Instant;
import java.util.UUID;

/** Public view of a user. Never contains the password hash. */
public record UserResponse(UUID id, String email, String displayName, Instant createdAt) {

    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getDisplayName(), user.getCreatedAt());
    }
}
