package com.infinitematters.bookkeeping.notifications;

import java.util.List;

public record CloseControlEscalationRunResult(
        int createdCount,
        List<NotificationSummary> notifications) {
}
