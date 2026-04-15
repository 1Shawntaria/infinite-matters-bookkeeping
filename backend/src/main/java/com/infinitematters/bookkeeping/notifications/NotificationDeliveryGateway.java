package com.infinitematters.bookkeeping.notifications;

public interface NotificationDeliveryGateway {
    boolean supports(NotificationChannel channel);
    NotificationDeliveryReceipt deliver(Notification notification);
}
