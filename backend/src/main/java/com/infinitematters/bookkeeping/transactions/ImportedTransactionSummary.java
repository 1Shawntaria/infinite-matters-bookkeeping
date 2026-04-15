package com.infinitematters.bookkeeping.transactions;

import com.infinitematters.bookkeeping.domain.Category;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record ImportedTransactionSummary(
        UUID transactionId,
        LocalDate transactionDate,
        BigDecimal amount,
        String merchant,
        Category proposedCategory,
        Category finalCategory,
        String route,
        double confidenceScore,
        TransactionStatus status) {
}
