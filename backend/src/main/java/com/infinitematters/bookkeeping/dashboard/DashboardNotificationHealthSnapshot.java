package com.infinitematters.bookkeeping.dashboard;

import com.infinitematters.bookkeeping.notifications.NotificationSummary;

import java.util.List;

public record DashboardNotificationHealthSnapshot(
        long pendingCount,
        long failedCount,
        long bouncedCount,
        long deadLetterCount,
        long acknowledgedDeadLetterCount,
        long resolvedDeadLetterCount,
        long retryingCount,
        long suppressedDestinationCount,
        long needsRetryCount,
        long needsUnsuppressCount,
        DashboardDeadLetterSupportTaskSnapshot supportTasks,
        DashboardDeadLetterEffectivenessSnapshot supportEffectiveness,
        DashboardDeadLetterSupportPerformanceSnapshot supportPerformance,
        List<DashboardDeadLetterActionSummary> topSupportActions,
        List<NotificationSummary> recentResolvedDeadLetters,
        List<NotificationSummary> attentionNotifications) {
}
