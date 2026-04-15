package com.infinitematters.bookkeeping.transactions;

import java.util.List;

public record ImportBatchResult(
        int importedCount,
        int duplicateCount,
        int reviewRequiredCount,
        int postedCount,
        List<ImportedTransactionSummary> transactions) {
}
