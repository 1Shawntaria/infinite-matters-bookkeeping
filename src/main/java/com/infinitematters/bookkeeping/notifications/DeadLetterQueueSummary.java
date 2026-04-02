package com.infinitematters.bookkeeping.notifications;

import java.util.List;

public record DeadLetterQueueSummary(
        List<DeadLetterQueueItem> needsRetry,
        List<DeadLetterQueueItem> needsUnsuppress,
        List<DeadLetterQueueItem> acknowledged,
        List<DeadLetterQueueItem> recentlyResolved) {
}
