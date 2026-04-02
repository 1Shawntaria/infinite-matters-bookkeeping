package com.infinitematters.bookkeeping.close;

public record CloseChecklistItem(
        String itemType,
        String label,
        boolean complete,
        String detail) {
}
