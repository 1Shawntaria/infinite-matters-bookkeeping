package com.infinitematters.bookkeeping.notifications;

public record DeadLetterSupportPerformanceTaskStatus(
        long openRiskTaskCount,
        long acknowledgedRiskTaskCount,
        long snoozedRiskTaskCount,
        long ignoredRiskTaskCount,
        long secondaryEscalationCount) {
}
