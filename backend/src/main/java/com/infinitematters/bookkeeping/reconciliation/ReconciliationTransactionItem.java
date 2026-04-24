package com.infinitematters.bookkeeping.reconciliation;

import com.infinitematters.bookkeeping.transactions.TransactionStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record ReconciliationTransactionItem(
        UUID transactionId,
        LocalDate transactionDate,
        BigDecimal amount,
        String merchant,
        String memo,
        TransactionStatus status) {
}
