package com.infinitematters.bookkeeping.notifications;

import java.util.List;

public record NotificationOperationsSummary(
        long pendingCount,
        long failedCount,
        long bouncedCount,
        long deadLetterCount,
        long retryingCount,
        long suppressedDestinationCount,
        List<NotificationSummary> attentionNotifications,
        DeadLetterOperationsSummary deadLetterOperations) {
}
