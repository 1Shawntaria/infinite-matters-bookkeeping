package com.infinitematters.bookkeeping.dashboard;

import com.infinitematters.bookkeeping.notifications.DeadLetterSupportPerformanceStatus;
import com.infinitematters.bookkeeping.workflows.ReviewTaskSummary;

import java.util.List;

public record DashboardDeadLetterSupportPerformanceSnapshot(
        String cardId,
        int weeks,
        long escalatedCount,
        long openRiskTaskCount,
        long acknowledgedRiskTaskCount,
        long snoozedRiskTaskCount,
        long ignoredRiskTaskCount,
        long secondaryEscalationCount,
        long recentlyReactivatedCount,
        long recentlyReactivatedNeedsAttentionCount,
        long freshlyReactivatedNeedsAttentionCount,
        long reactivatedOverdueCount,
        double ignoredEscalationRate,
        Double averageAssignmentLagHours,
        Double averageResolutionLagHours,
        boolean ignoredEscalationRateBreached,
        boolean assignmentLagBreached,
        boolean resolutionLagBreached,
        DeadLetterSupportPerformanceStatus status,
        long urgentRiskTaskCount,
        String recommendedActionLabel,
        String recommendedActionKey,
        String recommendedActionPath,
        DashboardActionUrgency recommendedActionUrgency,
        List<ReviewTaskSummary> urgentRiskTasks,
        List<DashboardDeadLetterSupportPerformanceReactivationItem> recentReactivations,
        List<DashboardDeadLetterSupportPerformanceReactivationItem> recentReactivationsNeedingAttention) {
}
