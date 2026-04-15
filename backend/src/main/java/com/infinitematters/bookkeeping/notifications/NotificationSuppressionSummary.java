package com.infinitematters.bookkeeping.notifications;

import java.time.Instant;
import java.util.UUID;

public record NotificationSuppressionSummary(
        UUID suppressionId,
        String email,
        String providerName,
        String reason,
        UUID sourceNotificationId,
        Instant lastEventAt,
        Instant createdAt) {
    public static NotificationSuppressionSummary from(NotificationSuppression suppression) {
        return new NotificationSuppressionSummary(
                suppression.getId(),
                suppression.getEmail(),
                suppression.getProviderName(),
                suppression.getReason(),
                suppression.getSourceNotification() != null ? suppression.getSourceNotification().getId() : null,
                suppression.getLastEventAt(),
                suppression.getCreatedAt());
    }
}
