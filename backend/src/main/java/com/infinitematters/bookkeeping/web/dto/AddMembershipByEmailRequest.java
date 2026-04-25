package com.infinitematters.bookkeeping.web.dto;

import com.infinitematters.bookkeeping.users.UserRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record AddMembershipByEmailRequest(
        @NotNull UUID organizationId,
        @NotBlank @Email String email,
        @NotNull UserRole role) {
}
