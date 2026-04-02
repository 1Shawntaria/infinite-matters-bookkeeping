package com.infinitematters.bookkeeping.web.dto;

import com.infinitematters.bookkeeping.users.UserRole;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record AddMembershipRequest(
        @NotNull UUID organizationId,
        @NotNull UUID userId,
        @NotNull UserRole role) {
}
