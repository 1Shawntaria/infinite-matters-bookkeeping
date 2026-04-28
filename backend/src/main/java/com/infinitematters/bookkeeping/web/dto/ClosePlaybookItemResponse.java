package com.infinitematters.bookkeeping.web.dto;

import com.infinitematters.bookkeeping.organization.PeriodClosePlaybookItemService;

import java.time.Instant;
import java.util.UUID;

public record ClosePlaybookItemResponse(
        UUID id,
        UUID templateItemId,
        String month,
        String label,
        String guidance,
        int sortOrder,
        AssigneeSummary assignee,
        AssigneeSummary approver,
        Instant completedAt,
        AssigneeSummary completedBy,
        Instant approvedAt,
        AssigneeSummary approvedBy,
        Instant createdAt,
        boolean completed,
        boolean approved,
        boolean satisfied) {

    public static ClosePlaybookItemResponse from(PeriodClosePlaybookItemService.PeriodClosePlaybookItemView view) {
        return new ClosePlaybookItemResponse(
                view.id(),
                view.templateItemId(),
                view.month(),
                view.label(),
                view.guidance(),
                view.sortOrder(),
                AssigneeSummary.from(view.assignee()),
                AssigneeSummary.from(view.approver()),
                view.completedAt(),
                AssigneeSummary.from(view.completedBy()),
                view.approvedAt(),
                AssigneeSummary.from(view.approvedBy()),
                view.createdAt(),
                view.isCompleted(),
                view.isApproved(),
                view.isSatisfied());
    }

    public record AssigneeSummary(UUID id, String email, String fullName) {
        static AssigneeSummary from(PeriodClosePlaybookItemService.UserAssignmentSummary summary) {
            if (summary == null) {
                return null;
            }
            return new AssigneeSummary(summary.id(), summary.email(), summary.fullName());
        }
    }
}
