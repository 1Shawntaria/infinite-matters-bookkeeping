package com.infinitematters.bookkeeping.web.dto;

import com.infinitematters.bookkeeping.organization.Organization;
import com.infinitematters.bookkeeping.organization.PlanTier;
import com.infinitematters.bookkeeping.users.OrganizationMembership;
import com.infinitematters.bookkeeping.users.UserRole;

import java.time.Instant;
import java.util.UUID;

public record OrganizationResponse(
        UUID id,
        String name,
        PlanTier planTier,
        String timezone,
        int invitationTtlDays,
        Instant createdAt,
        UserRole role) {
    public static OrganizationResponse from(Organization organization) {
        return new OrganizationResponse(
                organization.getId(),
                organization.getName(),
                organization.getPlanTier(),
                organization.getTimezone(),
                organization.getInvitationTtlDays(),
                organization.getCreatedAt(),
                null);
    }

    public static OrganizationResponse from(OrganizationMembership membership) {
        Organization organization = membership.getOrganization();
        return new OrganizationResponse(
                organization.getId(),
                organization.getName(),
                organization.getPlanTier(),
                organization.getTimezone(),
                organization.getInvitationTtlDays(),
                organization.getCreatedAt(),
                membership.getRole());
    }
}
