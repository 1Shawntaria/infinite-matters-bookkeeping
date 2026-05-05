import { AuditEventSummary } from "./api/audit";
import { NotificationSummaryItem } from "./api/auth";
import type {
    CloseControlDisposition,
    WorkflowAttentionTask,
} from "./api/notifications";

export type FollowUpSeverity = "routine" | "scheduled" | "escalated";

export type FollowUpAction = {
    severity: FollowUpSeverity;
    title: string;
    message: string;
    primaryHref: string;
    primaryLabel: string;
    secondaryHref: string;
    secondaryLabel: string;
    nextTouchLabel?: string;
    nextTouchReason?: string;
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
    attentionNotifications?: NotificationSummaryItem[];
    recommendedActionSeverity?: FollowUpSeverity | null;
    recommendedActionPath?: string | null;
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

export function closeFollowUpSeverityLabel(severity: FollowUpSeverity): string {
    switch (severity) {
        case "escalated":
            return "Escalated";
        case "scheduled":
            return "Scheduled";
        default:
            return "Routine";
    }
}

export function closeFollowUpSeverityTone(
    severity: FollowUpSeverity
): "success" | "warning" | "error" {
    switch (severity) {
        case "escalated":
            return "error";
        case "scheduled":
            return "warning";
        default:
            return "success";
    }
}

export function closeFollowUpSeverityClasses(severity: FollowUpSeverity): {
    card: string;
    badge: string;
    primaryButton: string;
    nextTouch: string;
} {
    switch (severity) {
        case "escalated":
            return {
                card: "border-rose-300/30 bg-rose-300/10",
                badge: "border border-rose-300/40 bg-rose-300/10 text-rose-100",
                primaryButton: "bg-rose-200 text-black hover:bg-rose-100",
                nextTouch: "text-rose-100/80",
            };
        case "scheduled":
            return {
                card: "border-amber-400/20 bg-amber-300/10",
                badge: "border border-amber-300/40 bg-amber-300/10 text-amber-100",
                primaryButton: "bg-amber-300 text-black hover:bg-amber-200",
                nextTouch: "text-amber-100",
            };
        default:
            return {
                card: "border-emerald-400/20 bg-emerald-300/10",
                badge: "border border-emerald-300/40 bg-emerald-300/10 text-emerald-100",
                primaryButton: "bg-emerald-300 text-black hover:bg-emerald-200",
                nextTouch: "text-emerald-100",
            };
    }
}

export function closeControlEscalationSeverity(
    notification: Pick<
        NotificationSummaryItem,
        "closeControlAcknowledgedAt" | "closeControlDisposition"
    >
): FollowUpSeverity {
    if (!notification.closeControlAcknowledgedAt) {
        return "escalated";
    }
    return normalizeCloseControlDisposition(notification.closeControlDisposition) === "REVISIT_TOMORROW"
        ? "scheduled"
        : "escalated";
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
        attentionNotifications = [],
        recommendedActionSeverity = null,
        recommendedActionPath = null,
    } = input;

    if (!focusMonth) {
        return null;
    }

    const focusMonthAttestationTask = workflowAttentionTasks.find(
        (task) =>
            task.taskType === "CLOSE_ATTESTATION_FOLLOW_UP" &&
            workflowTaskMonth(task) === focusMonth
    );
    const focusMonthEscalation = attentionNotifications.find(
        (notification) =>
            isEscalatedCloseControlNotification(notification) &&
            !notification.closeControlResolvedAt &&
            notificationMonth(notification, workflowAttentionTasks) === focusMonth
    );

    if (focusMonthEscalation?.closeControlAcknowledgedAt) {
        const reviewedDisposition = normalizeCloseControlDisposition(
            focusMonthEscalation.closeControlDisposition
        );
        const reviewedNextTouchDate = getCloseControlNextTouchDate(
            focusMonthEscalation,
            workflowAttentionTasks
        );
        return {
            severity: resolveRecommendedSeverity(
                reviewedDisposition === "REVISIT_TOMORROW" ? "scheduled" : "escalated",
                recommendedActionSeverity,
                recommendedActionPath,
                focusMonthEscalation.referenceId
                    ? buildEscalatedCloseControlAction(focusMonthEscalation, workflowAttentionTasks).primaryHref
                    : `/close?month=${encodeURIComponent(focusMonth)}`
            ),
            title: reviewedEscalationTitle(reviewedDisposition, context),
            message: reviewedEscalationMessage(
                focusMonth,
                reviewedDisposition,
                focusMonthEscalation.closeControlAcknowledgementNote,
                reviewedNextTouchDate
            ),
            primaryHref:
                focusMonthEscalation.referenceId
                    ? buildEscalatedCloseControlAction(focusMonthEscalation, workflowAttentionTasks).primaryHref
                    : `/close?month=${encodeURIComponent(focusMonth)}`,
            primaryLabel: reviewedEscalationPrimaryLabel(reviewedDisposition),
            secondaryHref: "/notifications",
            secondaryLabel: reviewedEscalationSecondaryLabel(reviewedDisposition),
            nextTouchLabel: reviewedEscalationNextTouchLabel(
                reviewedDisposition,
                reviewedNextTouchDate
            ),
            nextTouchReason: reviewedEscalationNextTouchReason(
                reviewedDisposition,
                focusMonthEscalation,
                workflowAttentionTasks
            ),
        };
    }

    if (focusMonthEscalation) {
        return {
            severity: resolveRecommendedSeverity(
                "escalated",
                recommendedActionSeverity,
                recommendedActionPath,
                focusMonthEscalation.referenceId
                    ? buildEscalatedCloseControlAction(focusMonthEscalation, workflowAttentionTasks).primaryHref
                    : `/close?month=${encodeURIComponent(focusMonth)}`
            ),
            title:
                context === "dashboard"
                    ? "Escalated attestation needs owner follow-through"
                    : "Escalated attestation is still open",
            message: `The focus month already crossed the escalation threshold. Route ${focusMonth} back through owner/admin review before treating it like a routine attestation delay.`,
            primaryHref:
                focusMonthEscalation.referenceId
                    ? buildEscalatedCloseControlAction(focusMonthEscalation, workflowAttentionTasks).primaryHref
                    : `/close?month=${encodeURIComponent(focusMonth)}`,
            primaryLabel: "Open escalated month",
            secondaryHref: "/notifications",
            secondaryLabel: "Review escalation",
        };
    }

    if (focusMonthAttestationTask) {
        if (focusMonthAttestationTask.acknowledgedAt) {
            return {
                severity: resolveRecommendedSeverity(
                    "scheduled",
                    recommendedActionSeverity,
                    recommendedActionPath,
                    focusMonthAttestationTask.actionPath ?? `/close?month=${encodeURIComponent(focusMonth)}`
                ),
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
            severity: resolveRecommendedSeverity(
                "routine",
                recommendedActionSeverity,
                recommendedActionPath,
                focusMonthAttestationTask.actionPath ?? `/close?month=${encodeURIComponent(focusMonth)}`
            ),
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
                  severity: "routine",
                  title: "Focus month is ready for final approval",
                  message: `${focusMonth} is operationally clear on reconciliations. Use the close workspace to finish sign-off, attestation, and the final close decision.`,
                  primaryHref: `/close?month=${encodeURIComponent(focusMonth)}`,
                  primaryLabel: "Open close workspace",
                  secondaryHref: "/readiness",
                  secondaryLabel: "Review readiness",
              }
            : {
                  severity: "routine",
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
            severity: "routine",
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
            severity: "routine",
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
            severity: "routine",
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
            severity: "routine",
            title: "Finish the standing close playbook",
            message: `${pendingPlaybookCount} recurring close playbook item(s) still need completion or approval for ${focusMonth}.`,
            primaryHref: "/run-close",
            primaryLabel: "Resume guided close",
            secondaryHref: `/close?month=${encodeURIComponent(focusMonth)}`,
            secondaryLabel: "Open close workspace",
        };
    }

    return {
        severity: "routine",
        title: "The month is ready for final close",
        message: `The focus month is in a good place to close once the owner is comfortable committing ${focusMonth}.`,
        primaryHref: `/close?month=${encodeURIComponent(focusMonth)}`,
        primaryLabel: "Approve and close",
        secondaryHref: "/activity",
        secondaryLabel: "Review control history",
    };
}

function resolveRecommendedSeverity(
    fallbackSeverity: FollowUpSeverity,
    recommendedSeverity: FollowUpSeverity | null,
    recommendedActionPath: string | null,
    actionHref: string
): FollowUpSeverity {
    if (recommendedSeverity && recommendedActionPath && recommendedActionPath === actionHref) {
        return recommendedSeverity;
    }
    return fallbackSeverity;
}

export function buildAuditCloseControlFollowUp(
    closeControlEvents: AuditEventSummary[],
    workflowAttentionTasks: WorkflowAttentionTask[] = []
): FollowUpAction | null {
    const latestForceCloseTask = workflowAttentionTasks.find(
        (task) => task.taskType === "FORCE_CLOSE_REVIEW"
    );
    if (latestForceCloseTask) {
        const nextTouchDate = latestForceCloseTask.dueDate;
        return {
            severity: latestForceCloseTask.acknowledgedAt ? "scheduled" : "escalated",
            title: latestForceCloseTask.acknowledgedAt
                ? "Force-close review is already in motion"
                : "Review the latest override month",
            message: latestForceCloseTask.acknowledgedAt
                ? `${workflowTaskMonth(latestForceCloseTask) ?? "The latest override month"} has already been reviewed by an operator, but the control-quality signal remains open until that review is cleared.`
                : `The most recent exception landed on ${workflowTaskMonth(latestForceCloseTask) ?? "a recent month"}. Revisit that month’s close workspace and confirm whether the override story is fully documented.`,
            primaryHref: latestForceCloseTask.actionPath ?? "/activity",
            primaryLabel: buildCloseControlTaskActionLabel(
                latestForceCloseTask.taskType,
                nextTouchDate
            ),
            secondaryHref: "/notifications",
            secondaryLabel: "Review workflow inbox",
            nextTouchLabel: nextTouchDate
                ? `Planned next touch: ${formatCalendarDate(nextTouchDate)}`
                : undefined,
        };
    }

    const latestUnconfirmedAttestationTask = workflowAttentionTasks.find(
        (task) => task.taskType === "CLOSE_ATTESTATION_FOLLOW_UP"
    );
    if (latestUnconfirmedAttestationTask) {
        const nextTouchDate = latestUnconfirmedAttestationTask.dueDate;
        return {
            severity: latestUnconfirmedAttestationTask.acknowledgedAt ? "scheduled" : "routine",
            title: latestUnconfirmedAttestationTask.acknowledgedAt
                ? "Attestation follow-through is being worked"
                : "Finish attestation follow-through",
            message: latestUnconfirmedAttestationTask.acknowledgedAt
                ? `${workflowTaskMonth(latestUnconfirmedAttestationTask) ?? "The focus month"} has been reviewed, but the final attestation confirmation is still missing.`
                : `${workflowTaskMonth(latestUnconfirmedAttestationTask) ?? "A recent month"} still shows an attestation update without a later confirmation. Push that month back through the assigned approver handoff.`,
            primaryHref:
                latestUnconfirmedAttestationTask.actionPath ??
                `/close?month=${encodeURIComponent(workflowTaskMonth(latestUnconfirmedAttestationTask) ?? "")}`,
            primaryLabel: buildCloseControlTaskActionLabel(
                latestUnconfirmedAttestationTask.taskType,
                nextTouchDate
            ),
            secondaryHref: "/notifications",
            secondaryLabel: "Review workflow inbox",
            nextTouchLabel: nextTouchDate
                ? `Planned next touch: ${formatCalendarDate(nextTouchDate)}`
                : undefined,
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
    notification: Pick<
        NotificationSummaryItem,
        "referenceId" | "message" | "closeControlDisposition" | "closeControlNextTouchOn"
    >,
    workflowAttentionTasks: WorkflowAttentionTask[] = []
): EscalatedCloseControlAction {
    const matchedTask = matchingEscalatedCloseControlTask(notification, workflowAttentionTasks);
    const month = matchedTask ? workflowTaskMonth(matchedTask) : null;
    const monthLabel = month ?? "the affected month";
    const primaryHref =
        matchedTask?.actionPath ??
        (month ? `/close?month=${encodeURIComponent(month)}` : "/notifications");
    const primaryLabel = buildCloseControlTaskActionLabel(
        matchedTask?.taskType ?? null,
        getCloseControlNextTouchDate(notification, workflowAttentionTasks) ?? matchedTask?.dueDate ?? null
    );

    if (matchedTask?.taskType === "FORCE_CLOSE_REVIEW") {
        return {
            title: "Escalated force-close review",
            message: `${monthLabel} still carries an unresolved override review after repeated reminders. Owner or admin follow-through is now required.`,
            primaryHref,
            primaryLabel,
            secondaryHref: "/notifications",
            secondaryLabel: "Review workflow inbox",
            monthLabel,
        };
    }

    return {
        title: "Escalated attestation follow-up",
        message: `${monthLabel} still lacks final attestation confirmation after repeated reminders. Move the month back through owner/admin review now.`,
        primaryHref,
        primaryLabel,
        secondaryHref: "/notifications",
        secondaryLabel: "Review workflow inbox",
        monthLabel,
    };
}

export function buildCloseControlTaskActionLabel(
    taskType: string | null | undefined,
    dueDate: string | null | undefined
): string {
    const formattedDate = dueDate ? formatShortDate(dueDate) : null;
    if (taskType === "FORCE_CLOSE_REVIEW") {
        return formattedDate
            ? `Resume override review on ${formattedDate}`
            : "Resume override review";
    }
    if (taskType === "CLOSE_ATTESTATION_FOLLOW_UP") {
        return formattedDate
            ? `Revisit attestation on ${formattedDate}`
            : "Revisit attestation";
    }
    return "Open workflow task";
}

function normalizeCloseControlDisposition(
    disposition: string | null | undefined
): CloseControlDisposition {
    if (
        disposition === "WAITING_ON_APPROVER" ||
        disposition === "OVERRIDE_DOCS_IN_PROGRESS" ||
        disposition === "REVISIT_TOMORROW"
    ) {
        return disposition;
    }
    return "WAITING_ON_APPROVER";
}

function reviewedEscalationTitle(
    disposition: CloseControlDisposition,
    context: "dashboard" | "readiness"
): string {
    if (disposition === "OVERRIDE_DOCS_IN_PROGRESS") {
        return context === "dashboard"
            ? "Override documentation is under owner review"
            : "Override documentation is being finalized";
    }
    if (disposition === "REVISIT_TOMORROW") {
        return context === "dashboard"
            ? "Escalated attestation is queued for tomorrow"
            : "Escalation reviewed, revisit tomorrow";
    }
    return context === "dashboard"
        ? "Escalated attestation is under owner review"
        : "Escalation reviewed, waiting on follow-through";
}

function reviewedEscalationMessage(
    focusMonth: string,
    disposition: CloseControlDisposition,
    note: string | null,
    nextTouchDate?: string | null
): string {
    const noteSuffix = note ? `: ${note}` : ".";
    if (disposition === "OVERRIDE_DOCS_IN_PROGRESS") {
        return `Owner/admin review is already on record for ${focusMonth}. Override support and documentation are in progress${noteSuffix}`;
    }
    if (disposition === "REVISIT_TOMORROW") {
        const nextTouchSuffix = nextTouchDate
            ? ` until ${formatCalendarDate(nextTouchDate)}`
            : " until the next review window";
        return `Owner/admin review is already on record for ${focusMonth}. The workflow is intentionally parked${nextTouchSuffix}${noteSuffix}`;
    }
    return note
        ? `Owner/admin review is already on record for ${focusMonth}: ${note}`
        : `An owner or admin already reviewed the escalation for ${focusMonth}. Keep the month moving against that plan before triggering more escalation churn.`;
}

function reviewedEscalationPrimaryLabel(
    disposition: CloseControlDisposition
): string {
    if (disposition === "OVERRIDE_DOCS_IN_PROGRESS") {
        return "Open documentation month";
    }
    if (disposition === "REVISIT_TOMORROW") {
        return "Open follow-up month";
    }
    return "Open reviewed month";
}

function reviewedEscalationSecondaryLabel(
    disposition: CloseControlDisposition
): string {
    if (disposition === "REVISIT_TOMORROW") {
        return "Review follow-up timing";
    }
    return "Review escalation note";
}

function reviewedEscalationNextTouchLabel(
    disposition: CloseControlDisposition,
    nextTouchDate?: string | null
): string | undefined {
    if (disposition !== "REVISIT_TOMORROW" || !nextTouchDate) {
        return undefined;
    }
    return `Planned next touch: ${formatCalendarDate(nextTouchDate)}`;
}

function reviewedEscalationNextTouchReason(
    disposition: CloseControlDisposition,
    notification: Pick<
        NotificationSummaryItem,
        "referenceId" | "message" | "closeControlDisposition"
    >,
    workflowAttentionTasks: WorkflowAttentionTask[]
): string | undefined {
    if (disposition !== "REVISIT_TOMORROW") {
        return undefined;
    }
    const matchedTask = matchingEscalatedCloseControlTask(notification, workflowAttentionTasks);
    if (!matchedTask) {
        return "The system queued the next review window automatically so this escalation stays visible without restarting the work early.";
    }
    if (matchedTask.taskType === "FORCE_CLOSE_REVIEW") {
        return matchedTask.overdue
            ? "This suggestion stays on the next review day because the override review is already overdue and still needs owner follow-through."
            : "This suggestion gives override documentation time to finish before the month gets pulled back into active review.";
    }
    return matchedTask.overdue
        ? "This suggestion keeps attestation follow-through on the next review day because the month is already behind on final confirmation."
        : "This suggestion follows the attestation due date so the approver handoff stays on track without creating extra churn.";
}

function notificationMonth(
    notification: Pick<NotificationSummaryItem, "referenceId" | "message">,
    workflowAttentionTasks: WorkflowAttentionTask[]
): string | null {
    const matchedTask = matchingEscalatedCloseControlTask(notification, workflowAttentionTasks);
    if (matchedTask) {
        return workflowTaskMonth(matchedTask);
    }
    const messageMatch = notification.message.match(/\b(\d{4}-\d{2})\b/);
    return messageMatch ? messageMatch[1] : null;
}

export function getCloseControlNextTouchDate(
    notification: Pick<
        NotificationSummaryItem,
        "referenceId" | "message" | "closeControlDisposition" | "closeControlNextTouchOn"
    >,
    workflowAttentionTasks: WorkflowAttentionTask[]
): string | null {
    if (normalizeCloseControlDisposition(notification.closeControlDisposition) !== "REVISIT_TOMORROW") {
        return null;
    }
    return notification.closeControlNextTouchOn
        ?? matchingEscalatedCloseControlTask(notification, workflowAttentionTasks)?.dueDate
        ?? null;
}

function matchingEscalatedCloseControlTask(
    notification: Pick<NotificationSummaryItem, "referenceId" | "message">,
    workflowAttentionTasks: WorkflowAttentionTask[]
): WorkflowAttentionTask | null {
    return workflowAttentionTasks.find((task) => task.taskId === notification.referenceId) ?? null;
}

function formatCalendarDate(value: string): string {
    return new Date(`${value}T00:00:00`).toLocaleDateString("en-US", {
        month: "short",
        day: "numeric",
        year: "numeric",
    });
}

function formatShortDate(value: string): string {
    return new Date(`${value}T00:00:00`).toLocaleDateString("en-US", {
        month: "short",
        day: "numeric",
    });
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
