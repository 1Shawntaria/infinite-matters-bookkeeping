package com.infinitematters.bookkeeping.web.dto;

import com.infinitematters.bookkeeping.notifications.NotificationSummary;

import java.time.Instant;
import java.util.UUID;

public record InvitationDeliverySummary(
        UUID notificationId,
        String category,
        String channel,
        String status,
        String deliveryState,
        int attemptCount,
        String lastError,
        String lastFailureCode,
        String providerName,
        String providerMessageId,
        Instant scheduledFor,
        Instant lastAttemptedAt,
        Instant sentAt,
        Instant createdAt) {
    public static InvitationDeliverySummary from(NotificationSummary notification) {
        return new InvitationDeliverySummary(
                notification.id(),
                notification.category().name(),
                notification.channel().name(),
                notification.status().name(),
                notification.deliveryState().name(),
                notification.attemptCount(),
                notification.lastError(),
                notification.lastFailureCode(),
                notification.providerName(),
                notification.providerMessageId(),
                notification.scheduledFor(),
                notification.lastAttemptedAt(),
                notification.sentAt(),
                notification.createdAt());
    }
}
