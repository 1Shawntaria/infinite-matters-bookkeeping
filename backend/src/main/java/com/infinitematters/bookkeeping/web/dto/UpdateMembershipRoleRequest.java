package com.infinitematters.bookkeeping.web.dto;

import com.infinitematters.bookkeeping.users.UserRole;
import jakarta.validation.constraints.NotNull;

public record UpdateMembershipRoleRequest(
        @NotNull UserRole role) {
}
