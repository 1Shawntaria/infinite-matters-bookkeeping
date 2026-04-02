package com.infinitematters.bookkeeping.ledger;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record LedgerEntrySummary(
        UUID journalEntryId,
        UUID transactionId,
        LocalDate entryDate,
        String description,
        JournalEntryType entryType,
        String adjustmentReason,
        Instant createdAt,
        List<LedgerLineSummary> lines) {

    public record LedgerLineSummary(String accountCode, String accountName, EntrySide entrySide, BigDecimal amount) {
    }
}
