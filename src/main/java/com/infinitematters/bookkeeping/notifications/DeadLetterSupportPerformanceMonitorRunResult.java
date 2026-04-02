package com.infinitematters.bookkeeping.notifications;

public record DeadLetterSupportPerformanceMonitorRunResult(
        int createdCount,
        int closedCount) {
}
