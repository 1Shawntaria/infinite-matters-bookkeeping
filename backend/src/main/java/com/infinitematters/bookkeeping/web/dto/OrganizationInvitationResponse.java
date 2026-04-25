package com.infinitematters.bookkeeping.web.dto;

import com.infinitematters.bookkeeping.users.OrganizationInvitation;
import com.infinitematters.bookkeeping.users.OrganizationInvitationStatus;
import com.infinitematters.bookkeeping.users.UserRole;

import java.time.Instant;
import java.util.Optional;
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
        String inviteUrl,
        InvitationDeliverySummary delivery) {
    public static OrganizationInvitationResponse from(OrganizationInvitation invitation) {
        return from(invitation, null, null);
    }

    public static OrganizationInvitationResponse from(OrganizationInvitation invitation, String inviteUrl) {
        return from(invitation, inviteUrl, null);
    }

    public static OrganizationInvitationResponse from(OrganizationInvitation invitation,
                                                      String inviteUrl,
                                                      InvitationDeliverySummary delivery) {
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
                inviteUrl,
                delivery);
    }
}
