package com.infinitematters.bookkeeping.transactions;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record ImportedTransactionHistoryItem(
        UUID transactionId,
        UUID financialAccountId,
        String financialAccountName,
        Instant importedAt,
        LocalDate transactionDate,
        BigDecimal amount,
        String merchant,
        String proposedCategory,
        String finalCategory,
        String route,
        double confidenceScore,
        TransactionStatus status) {
}
