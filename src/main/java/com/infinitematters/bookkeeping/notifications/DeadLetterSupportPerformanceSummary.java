package com.infinitematters.bookkeeping.notifications;

import java.time.LocalDate;

public record DeadLetterSupportPerformanceSummary(
        LocalDate fromWeekStart,
        LocalDate toWeekEnd,
        int weeks,
        long escalatedCount,
        double ignoredEscalationRate,
        Double averageAssignmentLagHours,
        Double averageResolutionLagHours,
        boolean ignoredEscalationRateBreached,
        boolean assignmentLagBreached,
        boolean resolutionLagBreached,
        DeadLetterSupportPerformanceStatus status) {
}
