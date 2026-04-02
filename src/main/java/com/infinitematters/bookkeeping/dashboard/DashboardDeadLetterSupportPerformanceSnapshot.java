package com.infinitematters.bookkeeping.dashboard;

import com.infinitematters.bookkeeping.notifications.DeadLetterSupportPerformanceStatus;

public record DashboardDeadLetterSupportPerformanceSnapshot(
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
