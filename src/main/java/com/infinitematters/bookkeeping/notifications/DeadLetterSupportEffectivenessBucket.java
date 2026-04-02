package com.infinitematters.bookkeeping.notifications;

import java.time.LocalDate;

public record DeadLetterSupportEffectivenessBucket(
        LocalDate weekStart,
        LocalDate weekEnd,
        long escalatedCount,
        long ignoredEscalationCount,
        long assignedAfterEscalationCount,
        long resolvedAfterEscalationCount) {
}
