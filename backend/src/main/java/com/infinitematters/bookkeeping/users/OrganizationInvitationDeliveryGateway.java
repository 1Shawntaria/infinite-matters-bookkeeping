package com.infinitematters.bookkeeping.users;

public interface OrganizationInvitationDeliveryGateway {
    void sendInvitation(OrganizationInvitation invitation, String rawToken);
}
