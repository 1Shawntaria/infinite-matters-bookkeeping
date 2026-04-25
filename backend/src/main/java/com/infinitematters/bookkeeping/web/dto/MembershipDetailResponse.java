package com.infinitematters.bookkeeping.web.dto;

import com.infinitematters.bookkeeping.users.OrganizationMembership;
import com.infinitematters.bookkeeping.users.UserRole;

import java.time.Instant;
import java.util.UUID;

public record MembershipDetailResponse(
        UUID id,
        UUID organizationId,
        UserResponse user,
        UserRole role,
        Instant createdAt) {
    public static MembershipDetailResponse from(OrganizationMembership membership) {
        return new MembershipDetailResponse(
                membership.getId(),
                membership.getOrganization().getId(),
                UserResponse.from(membership.getUser()),
                membership.getRole(),
                membership.getCreatedAt());
    }
}
