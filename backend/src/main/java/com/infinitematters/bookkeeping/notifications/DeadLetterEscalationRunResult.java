package com.infinitematters.bookkeeping.notifications;

import java.util.List;

public record DeadLetterEscalationRunResult(
        int createdCount,
        List<NotificationSummary> notifications) {
}
