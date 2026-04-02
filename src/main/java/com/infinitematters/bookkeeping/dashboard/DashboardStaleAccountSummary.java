package com.infinitematters.bookkeeping.dashboard;

import com.infinitematters.bookkeeping.accounts.AccountType;

import java.time.LocalDate;
import java.util.UUID;

public record DashboardStaleAccountSummary(
        UUID accountId,
        String accountName,
        AccountType accountType,
        LocalDate lastTransactionDate,
        long daysSinceActivity) {
}
