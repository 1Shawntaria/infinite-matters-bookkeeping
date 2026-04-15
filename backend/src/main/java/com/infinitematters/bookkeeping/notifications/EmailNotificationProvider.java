package com.infinitematters.bookkeeping.notifications;

public interface EmailNotificationProvider {
    String providerKey();

    NotificationDeliveryReceipt send(Notification notification);
}
