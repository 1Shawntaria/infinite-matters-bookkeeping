package com.infinitematters.bookkeeping.dashboard;

import com.infinitematters.bookkeeping.notifications.DeadLetterSupportTaskSummary;

import java.util.List;

public record DashboardDeadLetterSupportTaskSnapshot(
        long openCount,
        long unassignedCount,
        long overdueCount,
        long staleCount,
        long escalatedCount,
        long ignoredEscalationCount,
        long assignedAfterEscalationCount,
        long resolvedAfterEscalationCount,
        List<DeadLetterSupportTaskSummary> oldestTasks) {
}
