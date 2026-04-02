package com.infinitematters.bookkeeping.notifications;

import java.util.List;

public record DeadLetterOperationsSummary(
        long openCount,
        long acknowledgedCount,
        long resolvedCount,
        List<NotificationSummary> recentResolvedNotifications) {
}
