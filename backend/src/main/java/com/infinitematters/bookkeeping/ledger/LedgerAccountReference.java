package com.infinitematters.bookkeeping.ledger;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record LedgerAccountReference(
        String accountCode,
        String accountName,
        String classification,
        List<String> sourceKinds,
        List<String> categoryHints,
        long activityEntryCount,
        LocalDate lastEntryDate,
        BigDecimal debitTotal,
        BigDecimal creditTotal) {
}
