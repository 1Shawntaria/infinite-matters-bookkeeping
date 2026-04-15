package com.infinitematters.bookkeeping.reconciliation;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record ReconciliationSummary(
        UUID id,
        UUID financialAccountId,
        String accountName,
        LocalDate periodStart,
        LocalDate periodEnd,
        BigDecimal openingBalance,
        BigDecimal statementEndingBalance,
        BigDecimal computedEndingBalance,
        BigDecimal varianceAmount,
        String notes,
        ReconciliationStatus status,
        Instant completedAt,
        Instant createdAt) {
}
