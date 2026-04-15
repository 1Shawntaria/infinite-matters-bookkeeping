package com.infinitematters.bookkeeping.notifications;

import org.springframework.stereotype.Component;

@Component
public class ProviderBackedEmailNotificationDeliveryGateway implements NotificationDeliveryGateway {
    private final EmailNotificationProviderSelector providerSelector;

    public ProviderBackedEmailNotificationDeliveryGateway(EmailNotificationProviderSelector providerSelector) {
        this.providerSelector = providerSelector;
    }

    @Override
    public boolean supports(NotificationChannel channel) {
        return channel == NotificationChannel.EMAIL;
    }

    @Override
    public NotificationDeliveryReceipt deliver(Notification notification) {
        return providerSelector.selectedProvider().send(notification);
    }
}
