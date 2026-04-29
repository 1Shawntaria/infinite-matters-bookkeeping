import { AuditEventSummary } from "./api/audit";
import { NotificationSummaryItem } from "./api/auth";
import type { WorkflowAttentionTask } from "./api/notifications";

export type FollowUpAction = {
    title: string;
    message: string;
    primaryHref: string;
    primaryLabel: string;
    secondaryHref: string;
    secondaryLabel: string;
};

export type EscalatedCloseControlAction = {
    title: string;
    message: string;
    primaryHref: string;
    primaryLabel: string;
    secondaryHref: string;
    secondaryLabel: string;
    monthLabel: string;
};

type FocusMonthFollowUpInput = {
    focusMonth: string;
    attestationRoutingGap: number;
    attestationGap: number;
    signoffGap: number;
    ownerSignoffGap: number;
    pendingPlaybookCount: number;
    requireOwnerSignoffBeforeClose: boolean;
    closeApproverName?: string | null;
    workflowAttentionTasks?: WorkflowAttentionTask[];
    context?: "dashboard" | "readiness";
    closeReady?: boolean;
    unreconciledAccountCount?: number;
};

const CLOSE_CONTROL_EVENT_TYPES = new Set([
    "PERIOD_CLOSE_ATTESTATION_UPDATED",
    "PERIOD_CLOSE_ATTESTED",
    "PERIOD_CLOSED",
    "PERIOD_FORCE_CLOSED",
]);

export function getCloseControlEvents(events: AuditEventSummary[]): AuditEventSummary[] {
    return events.filter((item) => CLOSE_CONTROL_EVENT_TYPES.has(item.eventType));
}

export function buildFocusMonthFollowUp(input: FocusMonthFollowUpInput): FollowUpAction | null {
    const {
        focusMonth,
        attestationRoutingGap,
        attestationGap,
        signoffGap,
        ownerSignoffGap,
        pendingPlaybookCount,
        requireOwnerSignoffBeforeClose,
        closeApproverName,
        workflowAttentionTasks = [],
        context = "readiness",
        closeReady = false,
        unreconciledAccountCount = 0,
    } = input;

    if (!focusMonth) {
        return null;
    }

    const focusMonthAttestationTask = workflowAttentionTasks.find(
        (task) =>
            task.taskType === "CLOSE_ATTESTATION_FOLLOW_UP" &&
            workflowTaskMonth(task) === focusMonth
    );

    if (focusMonthAttestationTask) {
        if (focusMonthAttestationTask.acknowledgedAt) {
            return {
                title:
                    context === "dashboard"
                        ? "Final attestation is waiting on approval"
                        : "Attestation is reviewed and awaiting confirmation",
                message: focusMonthAttestationTask.assignedToUserName
                    ? `${focusMonthAttestationTask.assignedToUserName} has already reviewed the attestation follow-up for ${focusMonth}. Keep the month open until the final confirmation is recorded.`
                    : `The attestation follow-up for ${focusMonth} has been reviewed, but the final confirmation still is not on record.`,
                primaryHref: focusMonthAttestationTask.actionPath ?? `/close?month=${encodeURIComponent(focusMonth)}`,
                primaryLabel: "Open attestation",
                secondaryHref: "/notifications",
                secondaryLabel: "Review workflow inbox",
            };
        }

        return {
            title:
                context === "dashboard"
                    ? "Push attestation through final approval"
                    : "Finish month-end attestation",
            message: focusMonthAttestationTask.assignedToUserName
                ? `${focusMonthAttestationTask.assignedToUserName} still needs to confirm the month-end attestation for ${focusMonth}.`
                : `The focus month still needs final month-level attestation confirmation.`,
            primaryHref: focusMonthAttestationTask.actionPath ?? `/close?month=${encodeURIComponent(focusMonth)}`,
            primaryLabel: "Open attestation",
            secondaryHref: "/notifications",
            secondaryLabel: "Review workflow inbox",
        };
    }

    if (context === "dashboard") {
        return closeReady
            ? {
                  title: "Focus month is ready for final approval",
                  message: `${focusMonth} is operationally clear on reconciliations. Use the close workspace to finish sign-off, attestation, and the final close decision.`,
                  primaryHref: `/close?month=${encodeURIComponent(focusMonth)}`,
                  primaryLabel: "Open close workspace",
                  secondaryHref: "/readiness",
                  secondaryLabel: "Review readiness",
              }
            : {
                  title: "Focus month still needs close follow-through",
                  message: `${focusMonth} still has ${unreconciledAccountCount} account(s) holding close open. Start with the close workflow for that month so the next blocker is obvious.`,
                  primaryHref: `/close?month=${encodeURIComponent(focusMonth)}`,
                  primaryLabel: "Open focus month",
                  secondaryHref: "/run-close",
                  secondaryLabel: "Resume guided close",
              };
    }

    if (attestationRoutingGap > 0) {
        return {
            title: "Fix attestation routing first",
            message: "Assign a close owner and a different approver for the focus month before expecting the final month-end certification to clear.",
            primaryHref: `/close?month=${encodeURIComponent(focusMonth)}`,
            primaryLabel: "Open close workspace",
            secondaryHref: "/run-close",
            secondaryLabel: "Review close runbook",
        };
    }

    if (attestationGap > 0) {
        return {
            title: "Finish month-end attestation",
            message: closeApproverName
                ? `${closeApproverName} is assigned to confirm the month-level attestation for ${focusMonth}.`
                : `The focus month still needs final month-level attestation confirmation.`,
            primaryHref: `/close?month=${encodeURIComponent(focusMonth)}`,
            primaryLabel: "Open attestation",
            secondaryHref: "/activity",
            secondaryLabel: "Review control history",
        };
    }

    if (signoffGap > 0 || ownerSignoffGap > 0) {
        return {
            title: "Capture the remaining sign-off",
            message: requireOwnerSignoffBeforeClose
                ? "Record the required approvals, including at least one owner sign-off, before treating the month as ready to close."
                : "Record the remaining formal sign-off before treating the month as ready to close.",
            primaryHref: `/close?month=${encodeURIComponent(focusMonth)}`,
            primaryLabel: "Record sign-off",
            secondaryHref: "/activity",
            secondaryLabel: "Review prior approvals",
        };
    }

    if (pendingPlaybookCount > 0) {
        return {
            title: "Finish the standing close playbook",
            message: `${pendingPlaybookCount} recurring close playbook item(s) still need completion or approval for ${focusMonth}.`,
            primaryHref: "/run-close",
            primaryLabel: "Resume guided close",
            secondaryHref: `/close?month=${encodeURIComponent(focusMonth)}`,
            secondaryLabel: "Open close workspace",
        };
    }

    return {
        title: "The month is ready for final close",
        message: `The focus month is in a good place to close once the owner is comfortable committing ${focusMonth}.`,
        primaryHref: `/close?month=${encodeURIComponent(focusMonth)}`,
        primaryLabel: "Approve and close",
        secondaryHref: "/activity",
        secondaryLabel: "Review control history",
    };
}

export function buildAuditCloseControlFollowUp(
    closeControlEvents: AuditEventSummary[],
    workflowAttentionTasks: WorkflowAttentionTask[] = []
): FollowUpAction | null {
    const latestForceCloseTask = workflowAttentionTasks.find(
        (task) => task.taskType === "FORCE_CLOSE_REVIEW"
    );
    if (latestForceCloseTask) {
        return {
            title: latestForceCloseTask.acknowledgedAt
                ? "Force-close review is already in motion"
                : "Review the latest override month",
            message: latestForceCloseTask.acknowledgedAt
                ? `${workflowTaskMonth(latestForceCloseTask) ?? "The latest override month"} has already been reviewed by an operator, but the control-quality signal remains open until that review is cleared.`
                : `The most recent exception landed on ${workflowTaskMonth(latestForceCloseTask) ?? "a recent month"}. Revisit that month’s close workspace and confirm whether the override story is fully documented.`,
            primaryHref: latestForceCloseTask.actionPath ?? "/activity",
            primaryLabel: "Open override month",
            secondaryHref: "/notifications",
            secondaryLabel: "Review workflow inbox",
        };
    }

    const latestUnconfirmedAttestationTask = workflowAttentionTasks.find(
        (task) => task.taskType === "CLOSE_ATTESTATION_FOLLOW_UP"
    );
    if (latestUnconfirmedAttestationTask) {
        return {
            title: latestUnconfirmedAttestationTask.acknowledgedAt
                ? "Attestation follow-through is being worked"
                : "Finish attestation follow-through",
            message: latestUnconfirmedAttestationTask.acknowledgedAt
                ? `${workflowTaskMonth(latestUnconfirmedAttestationTask) ?? "The focus month"} has been reviewed, but the final attestation confirmation is still missing.`
                : `${workflowTaskMonth(latestUnconfirmedAttestationTask) ?? "A recent month"} still shows an attestation update without a later confirmation. Push that month back through the assigned approver handoff.`,
            primaryHref:
                latestUnconfirmedAttestationTask.actionPath ??
                `/close?month=${encodeURIComponent(workflowTaskMonth(latestUnconfirmedAttestationTask) ?? "")}`,
            primaryLabel: "Open attestation month",
            secondaryHref: "/notifications",
            secondaryLabel: "Review workflow inbox",
        };
    }

    void closeControlEvents;
    return null;
}

export function isEscalatedCloseControlNotification(
    notification: Pick<NotificationSummaryItem, "referenceType">
): boolean {
    return notification.referenceType === "close_control_follow_up_escalation";
}

export function buildEscalatedCloseControlAction(
    notification: Pick<NotificationSummaryItem, "referenceId" | "message">,
    workflowAttentionTasks: WorkflowAttentionTask[] = []
): EscalatedCloseControlAction {
    const matchedTask =
        workflowAttentionTasks.find((task) => task.taskId === notification.referenceId) ?? null;
    const month = matchedTask ? workflowTaskMonth(matchedTask) : null;
    const monthLabel = month ?? "the affected month";
    const primaryHref =
        matchedTask?.actionPath ??
        (month ? `/close?month=${encodeURIComponent(month)}` : "/notifications");

    if (matchedTask?.taskType === "FORCE_CLOSE_REVIEW") {
        return {
            title: "Escalated force-close review",
            message: `${monthLabel} still carries an unresolved override review after repeated reminders. Owner or admin follow-through is now required.`,
            primaryHref,
            primaryLabel: "Open override month",
            secondaryHref: "/notifications",
            secondaryLabel: "Review workflow inbox",
            monthLabel,
        };
    }

    return {
        title: "Escalated attestation follow-up",
        message: `${monthLabel} still lacks final attestation confirmation after repeated reminders. Move the month back through owner/admin review now.`,
        primaryHref,
        primaryLabel: "Open attestation month",
        secondaryHref: "/notifications",
        secondaryLabel: "Review workflow inbox",
        monthLabel,
    };
}

export function workflowTaskMonth(task: WorkflowAttentionTask): string | null {
    const explicitPath = task.actionPath ?? "";
    const monthMatch = explicitPath.match(/[?&]month=([^&]+)/);
    if (monthMatch) {
        return decodeURIComponent(monthMatch[1]);
    }

    const titleMatch = task.title.match(/\b(\d{4}-\d{2})\b/);
    return titleMatch ? titleMatch[1] : null;
}
