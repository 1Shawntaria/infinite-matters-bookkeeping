package com.infinitematters.bookkeeping.notifications;

import java.util.List;

public record ReminderRunResult(
        int createdCount,
        List<NotificationSummary> notifications) {
}
