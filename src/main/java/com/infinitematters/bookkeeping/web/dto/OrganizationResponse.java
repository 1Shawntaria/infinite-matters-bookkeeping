package com.infinitematters.bookkeeping.web.dto;

import com.infinitematters.bookkeeping.organization.Organization;
import com.infinitematters.bookkeeping.organization.PlanTier;

import java.time.Instant;
import java.util.UUID;

public record OrganizationResponse(UUID id, String name, PlanTier planTier, String timezone, Instant createdAt) {
    public static OrganizationResponse from(Organization organization) {
        return new OrganizationResponse(
                organization.getId(),
                organization.getName(),
                organization.getPlanTier(),
                organization.getTimezone(),
                organization.getCreatedAt());
    }
}
