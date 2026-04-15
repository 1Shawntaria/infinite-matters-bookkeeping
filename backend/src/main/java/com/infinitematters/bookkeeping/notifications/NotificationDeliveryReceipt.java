package com.infinitematters.bookkeeping.notifications;

public record NotificationDeliveryReceipt(
        String providerName,
        String providerMessageId) {
}
