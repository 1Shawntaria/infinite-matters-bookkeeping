package com.infinitematters.bookkeeping.dashboard;

import java.time.Instant;
import java.util.UUID;

public record DashboardDeadLetterSupportPerformanceReactivationItem(
        UUID taskId,
        String details,
        Instant reactivatedAt,
        boolean needsAttention,
        boolean overdue) {
}
