package com.infinitematters.bookkeeping.reconciliation;

import com.infinitematters.bookkeeping.accounts.AccountType;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record ReconciliationAccountDetail(
        String focusMonth,
        UUID financialAccountId,
        String accountName,
        String institutionName,
        AccountType accountType,
        String currency,
        boolean active,
        ReconciliationSummary session,
        BigDecimal bookEndingBalance,
        BigDecimal varianceAmount,
        long postedTransactionCount,
        long reviewRequiredCount,
        boolean canStartReconciliation,
        boolean canCompleteReconciliation,
        String statusMessage,
        List<ReconciliationTransactionItem> transactions) {
}
