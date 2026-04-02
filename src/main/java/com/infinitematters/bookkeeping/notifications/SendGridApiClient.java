package com.infinitematters.bookkeeping.notifications;

public interface SendGridApiClient {
    NotificationDeliveryReceipt send(Notification notification, String fromEmail);
}
