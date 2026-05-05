package com.infinitematters.bookkeeping.workflows;

import com.infinitematters.bookkeeping.domain.Category;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record ReviewTaskSummary(
        UUID taskId,
        UUID transactionId,
        UUID notificationId,
        String taskType,
        String priority,
        boolean overdue,
        String title,
        String description,
        LocalDate dueDate,
        UUID assignedToUserId,
        String assignedToUserName,
        String merchant,
        BigDecimal amount,
        LocalDate transactionDate,
        Category proposedCategory,
        double confidenceScore,
        String route,
        String actionPath,
        String resolutionComment,
        UUID acknowledgedByUserId,
        Instant acknowledgedAt,
        LocalDate snoozedUntil,
        UUID resolvedByUserId,
        Instant resolvedAt,
        CloseFollowUpSeverity closeControlSeverity) {
}
