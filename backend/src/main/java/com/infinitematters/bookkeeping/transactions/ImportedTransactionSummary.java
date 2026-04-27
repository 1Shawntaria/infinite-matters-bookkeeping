package com.infinitematters.bookkeeping.transactions;

import com.infinitematters.bookkeeping.domain.Category;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record ImportedTransactionSummary(
        UUID transactionId,
        UUID financialAccountId,
        String financialAccountName,
        LocalDate transactionDate,
        LocalDate postedDate,
        BigDecimal amount,
        String currency,
        String merchant,
        String memo,
        String mcc,
        Category proposedCategory,
        Category finalCategory,
        String route,
        double confidenceScore,
        TransactionStatus status,
        String sourceType,
        java.time.Instant importedAt) {
}
