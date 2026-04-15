package com.infinitematters.bookkeeping.dashboard;

import java.util.List;

public record DashboardDeadLetterEffectivenessSnapshot(
        int weeks,
        long escalatedCount,
        long ignoredEscalationCount,
        long assignedAfterEscalationCount,
        long resolvedAfterEscalationCount,
        List<DashboardDeadLetterEffectivenessBucket> recentWeeks) {
}
