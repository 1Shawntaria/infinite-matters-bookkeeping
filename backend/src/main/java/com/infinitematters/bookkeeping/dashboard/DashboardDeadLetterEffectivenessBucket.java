package com.infinitematters.bookkeeping.dashboard;

import java.time.LocalDate;

public record DashboardDeadLetterEffectivenessBucket(
        LocalDate weekStart,
        LocalDate weekEnd,
        long escalatedCount,
        long ignoredEscalationCount,
        long assignedAfterEscalationCount,
        long resolvedAfterEscalationCount) {
}
