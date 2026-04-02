package com.infinitematters.bookkeeping.notifications;

import java.util.List;

public record NotificationRequeueResult(
        int requeuedCount,
        List<NotificationSummary> notifications) {
}
