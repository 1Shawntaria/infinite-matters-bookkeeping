package com.infinitematters.bookkeeping.notifications;

public enum DeadLetterSupportPerformanceTaskFilter {
    ALL,
    ACKNOWLEDGED,
    UNACKNOWLEDGED,
    SNOOZED,
    ASSIGNED,
    UNASSIGNED,
    OVERDUE,
    IGNORED,
    REACTIVATED_NEEDS_ATTENTION,
    REACTIVATED_OVERDUE
}
