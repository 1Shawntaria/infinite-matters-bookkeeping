package com.infinitematters.bookkeeping.web.dto;

import com.infinitematters.bookkeeping.users.AppUser;

import java.time.Instant;
import java.util.UUID;

public record UserResponse(UUID id, String email, String fullName, Instant createdAt) {
    public static UserResponse from(AppUser user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getFullName(), user.getCreatedAt());
    }
}
