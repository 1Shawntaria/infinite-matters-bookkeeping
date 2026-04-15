package com.infinitematters.bookkeeping.notifications;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record DeadLetterSupportTaskSummary(
        UUID taskId,
        UUID notificationId,
        DeadLetterRecommendedAction recommendedAction,
        String recommendationReason,
        String priority,
        boolean overdue,
        boolean stale,
        boolean ignoredEscalation,
        boolean assignedAfterEscalation,
        boolean resolvedAfterEscalation,
        long ageDays,
        int escalationCount,
        Instant lastEscalatedAt,
        LocalDate dueDate,
        UUID assignedToUserId,
        String assignedToUserName,
        String recipientEmail,
        String title,
        String description) {
}
