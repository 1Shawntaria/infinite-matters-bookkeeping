package com.infinitematters.bookkeeping.notifications;

public record DeadLetterSupportPerformanceTaskQueueSummary(
        long openTaskCount,
        long assignedTaskCount,
        long unassignedTaskCount,
        long acknowledgedTaskCount,
        long unacknowledgedTaskCount,
        long snoozedTaskCount,
        long overdueTaskCount,
        long ignoredTaskCount,
        long reactivatedNeedsAttentionCount,
        long reactivatedOverdueCount,
        long secondaryEscalationCount) {
}
