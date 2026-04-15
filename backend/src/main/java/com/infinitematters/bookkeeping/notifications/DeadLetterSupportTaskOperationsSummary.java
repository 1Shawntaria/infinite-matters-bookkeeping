package com.infinitematters.bookkeeping.notifications;

import java.util.List;

public record DeadLetterSupportTaskOperationsSummary(
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
