package com.infinitematters.bookkeeping.periods;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record AccountingPeriodSummary(
        UUID id,
        LocalDate periodStart,
        LocalDate periodEnd,
        AccountingPeriodStatus status,
        PeriodCloseMethod closeMethod,
        String overrideReason,
        UUID overrideApprovedByUserId,
        Instant closedAt,
        Instant createdAt) {
}
