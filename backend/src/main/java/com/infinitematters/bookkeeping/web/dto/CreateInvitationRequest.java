package com.infinitematters.bookkeeping.web.dto;

import com.infinitematters.bookkeeping.users.UserRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateInvitationRequest(
        @NotNull UUID organizationId,
        @NotBlank @Email @Size(max = 320) String email,
        @NotNull UserRole role) {
}
