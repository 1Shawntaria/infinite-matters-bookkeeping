package com.infinitematters.bookkeeping.users;

import com.infinitematters.bookkeeping.notifications.NotificationCategory;
import com.infinitematters.bookkeeping.notifications.NotificationChannel;
import com.infinitematters.bookkeeping.notifications.NotificationService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Service
public class NotificationOrganizationInvitationDeliveryGateway implements OrganizationInvitationDeliveryGateway {
    private final NotificationService notificationService;
    private final String frontendBaseUrl;

    public NotificationOrganizationInvitationDeliveryGateway(
            NotificationService notificationService,
            @Value("${bookkeeping.frontend.base-url:http://localhost:3000}") String frontendBaseUrl) {
        this.notificationService = notificationService;
        this.frontendBaseUrl = frontendBaseUrl;
    }

    @Override
    public void sendInvitation(OrganizationInvitation invitation, String rawToken) {
        notificationService.sendOrganizationNotification(
                invitation.getOrganization().getId(),
                invitation.getEmail(),
                NotificationCategory.WORKSPACE_ACCESS,
                NotificationChannel.EMAIL,
                buildMessage(invitation, rawToken),
                "organization_invitation",
                invitation.getId().toString());
    }

    private String buildMessage(OrganizationInvitation invitation, String rawToken) {
        String inviteUrl = frontendBaseUrl.replaceAll("/+$", "") + "/invite/" + rawToken;
        String expiresAt = DateTimeFormatter.ISO_INSTANT.withZone(ZoneOffset.UTC)
                .format(invitation.getExpiresAt());
        return "You were invited to join " + invitation.getOrganization().getName()
                + " as " + invitation.getRole()
                + ". Open " + inviteUrl
                + " before " + expiresAt
                + " to accept the workspace invitation.";
    }
}
