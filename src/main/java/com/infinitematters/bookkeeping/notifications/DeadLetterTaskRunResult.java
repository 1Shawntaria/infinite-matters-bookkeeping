package com.infinitematters.bookkeeping.notifications;

public record DeadLetterTaskRunResult(
        int createdCount,
        int closedCount) {
}
