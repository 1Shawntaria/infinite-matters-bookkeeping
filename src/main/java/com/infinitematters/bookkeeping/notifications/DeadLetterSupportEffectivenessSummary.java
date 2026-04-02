package com.infinitematters.bookkeeping.notifications;

import java.time.LocalDate;
import java.util.List;

public record DeadLetterSupportEffectivenessSummary(
        LocalDate fromWeekStart,
        LocalDate toWeekEnd,
        int weeks,
        long escalatedCount,
        long ignoredEscalationCount,
        long assignedAfterEscalationCount,
        long resolvedAfterEscalationCount,
        List<DeadLetterSupportEffectivenessBucket> buckets) {
}
