import { AuditEventSummary } from "./api/audit";

export type FollowUpAction = {
    title: string;
    message: string;
    primaryHref: string;
    primaryLabel: string;
    secondaryHref: string;
    secondaryLabel: string;
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
        context = "readiness",
        closeReady = false,
        unreconciledAccountCount = 0,
    } = input;

    if (!focusMonth) {
        return null;
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

export function buildAuditCloseControlFollowUp(closeControlEvents: AuditEventSummary[]): FollowUpAction | null {
    const latestForceClose = closeControlEvents.find((item) => item.eventType === "PERIOD_FORCE_CLOSED");
    if (latestForceClose) {
        return {
            title: "Review the latest override month",
            message: `The most recent exception landed on ${latestForceClose.entityId}. Revisit that month’s close workspace and confirm whether the override story is fully documented.`,
            primaryHref: `/close?month=${encodeURIComponent(latestForceClose.entityId)}`,
            primaryLabel: "Open override month",
            secondaryHref: `/activity?lane=AUDIT&entityId=${encodeURIComponent(latestForceClose.entityId)}&label=${encodeURIComponent(`month ${latestForceClose.entityId}`)}`,
            secondaryLabel: "Trace audit history",
        };
    }

    const latestUnconfirmedAttestation = closeControlEvents.find(
        (item) =>
            item.eventType === "PERIOD_CLOSE_ATTESTATION_UPDATED" &&
            !closeControlEvents.some(
                (candidate) =>
                    candidate.eventType === "PERIOD_CLOSE_ATTESTED" &&
                    candidate.entityId === item.entityId &&
                    new Date(candidate.createdAt).getTime() >= new Date(item.createdAt).getTime()
            )
    );
    if (latestUnconfirmedAttestation) {
        return {
            title: "Finish attestation follow-through",
            message: `${latestUnconfirmedAttestation.entityId} still shows an attestation update without a later confirmation. Push that month back through the assigned approver handoff.`,
            primaryHref: `/close?month=${encodeURIComponent(latestUnconfirmedAttestation.entityId)}`,
            primaryLabel: "Open attestation month",
            secondaryHref: "/run-close",
            secondaryLabel: "Review close runbook",
        };
    }

    return null;
}
