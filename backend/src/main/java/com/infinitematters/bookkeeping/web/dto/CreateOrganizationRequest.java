package com.infinitematters.bookkeeping.web.dto;

import com.infinitematters.bookkeeping.organization.PlanTier;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateOrganizationRequest(
        @NotBlank @Size(max = 120) String name,
        @NotNull PlanTier planTier,
        @NotBlank @Size(max = 64) String timezone,
        @NotNull UUID ownerUserId) {
}
