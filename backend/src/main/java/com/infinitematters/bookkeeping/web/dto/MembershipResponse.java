package com.infinitematters.bookkeeping.web.dto;

import com.infinitematters.bookkeeping.users.OrganizationMembership;
import com.infinitematters.bookkeeping.users.UserRole;

import java.time.Instant;
import java.util.UUID;

public record MembershipResponse(
        UUID id,
        UUID organizationId,
        UUID userId,
        UserRole role,
        Instant createdAt) {
    public static MembershipResponse from(OrganizationMembership membership) {
        return new MembershipResponse(
                membership.getId(),
                membership.getOrganization().getId(),
                membership.getUser().getId(),
                membership.getRole(),
                membership.getCreatedAt());
    }
}
