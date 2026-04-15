package com.infinitematters.bookkeeping.close;

import java.time.LocalDate;
import java.util.List;

public record CloseChecklistSummary(
        LocalDate periodStart,
        LocalDate periodEnd,
        boolean closeReady,
        List<CloseChecklistItem> items) {
}
