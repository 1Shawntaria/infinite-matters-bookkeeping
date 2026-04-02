package com.infinitematters.bookkeeping.notifications;

public record DeadLetterQueueItem(
        NotificationSummary notification,
        DeadLetterRecommendedAction recommendedAction,
        boolean recipientSuppressed,
        NotificationSuppressionSummary suppression,
        String recommendationReason) {
}
