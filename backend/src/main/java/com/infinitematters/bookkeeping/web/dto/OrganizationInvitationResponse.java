package com.infinitematters.bookkeeping.web.dto;

import com.infinitematters.bookkeeping.users.OrganizationInvitation;
import com.infinitematters.bookkeeping.users.OrganizationInvitationStatus;
import com.infinitematters.bookkeeping.users.UserRole;

import java.time.Instant;
import java.util.UUID;

public record OrganizationInvitationResponse(
        UUID id,
        UUID organizationId,
        String organizationName,
        String email,
        UserRole role,
        OrganizationInvitationStatus status,
        Instant expiresAt,
        Instant acceptedAt,
        Instant revokedAt,
        Instant createdAt,
        UserResponse invitedByUser,
        UserResponse acceptedByUser,
        String inviteUrl) {
    public static OrganizationInvitationResponse from(OrganizationInvitation invitation) {
        return from(invitation, null);
    }

    public static OrganizationInvitationResponse from(OrganizationInvitation invitation, String inviteUrl) {
        return new OrganizationInvitationResponse(
                invitation.getId(),
                invitation.getOrganization().getId(),
                invitation.getOrganization().getName(),
                invitation.getEmail(),
                invitation.getRole(),
                invitation.getStatus(),
                invitation.getExpiresAt(),
                invitation.getAcceptedAt(),
                invitation.getRevokedAt(),
                invitation.getCreatedAt(),
                invitation.getInvitedByUser() != null ? UserResponse.from(invitation.getInvitedByUser()) : null,
                invitation.getAcceptedByUser() != null ? UserResponse.from(invitation.getAcceptedByUser()) : null,
                inviteUrl);
    }
}
