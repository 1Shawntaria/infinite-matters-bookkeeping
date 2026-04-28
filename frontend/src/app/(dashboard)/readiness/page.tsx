"use client";

import { useMemo } from "react";
import { useQuery } from "@tanstack/react-query";
import Link from "next/link";
import {
    LoadingPanel,
    NextStepsList,
    PageHero,
    ProgressMeter,
    SectionBand,
    StatusBanner,
    SummaryMetric,
} from "@/components/app-surfaces";
import { getDashboardSnapshot, DashboardSnapshot } from "@/lib/api/dashboard";
import { getReconciliationDashboard, ReconciliationDashboard } from "@/lib/api/reconciliation";
import { getReviewTasks, ReviewTask } from "@/lib/api/reviews";
import {
    AccountingPeriodSummary,
    CloseChecklistSummary,
    getCloseChecklist,
    listAccountingPeriods,
    listCloseNotes,
    listCloseSignoffs,
} from "@/lib/api/close";
import { listAttentionNotifications, listDeadLetterNotifications } from "@/lib/api/notifications";
import { useOrganizationSession } from "@/lib/auth/session";
import { AuditEventSummary } from "@/lib/api/audit";
import { NotificationSummaryItem } from "@/lib/api/auth";
import { getWorkspaceSettings } from "@/lib/api/settings";

type DecisionState =
    | "READY_TO_CLOSE"
    | "NEEDS_SIGNOFF"
    | "WAIT_FOR_BLOCKERS"
    | "FORCE_CLOSE_WITH_CAUTION"
    | "ALREADY_CLOSED";

type ReadinessDecision = {
    state: DecisionState;
    title: string;
    message: string;
    tone: "success" | "muted" | "error";
    primaryHref: string;
    primaryLabel: string;
    secondaryHref: string;
    secondaryLabel: string;
};

function clamp(value: number, min: number, max: number) {
    return Math.min(max, Math.max(min, value));
}

function scoreTone(score: number): "default" | "success" | "warning" {
    if (score >= 85) {
        return "success";
    }
    if (score >= 60) {
        return "default";
    }
    return "warning";
}

function riskTone(count: number): "default" | "success" | "warning" {
    return count === 0 ? "success" : "warning";
}

export default function CloseReadinessPage() {
    const { organizationId, hydrated } = useOrganizationSession();

    const dashboardQuery = useQuery<DashboardSnapshot, Error>({
        queryKey: ["dashboardSnapshot", organizationId],
        enabled: hydrated && Boolean(organizationId),
        queryFn: () => getDashboardSnapshot(organizationId),
    });

    const focusMonth = dashboardQuery.data?.focusMonth ?? "";

    const reviewTasksQuery = useQuery<ReviewTask[], Error>({
        queryKey: ["reviewTasks", organizationId],
        enabled: hydrated && Boolean(organizationId),
        queryFn: () => getReviewTasks(organizationId),
    });
    const reconciliationQuery = useQuery<ReconciliationDashboard, Error>({
        queryKey: ["reconciliationDashboard", organizationId],
        enabled: hydrated && Boolean(organizationId),
        queryFn: () => getReconciliationDashboard(organizationId),
    });
    const periodsQuery = useQuery<AccountingPeriodSummary[], Error>({
        queryKey: ["accountingPeriods", organizationId],
        enabled: hydrated && Boolean(organizationId),
        queryFn: () => listAccountingPeriods(organizationId),
    });
    const closeChecklistQuery = useQuery<CloseChecklistSummary, Error>({
        queryKey: ["closeChecklist", organizationId, focusMonth],
        enabled: hydrated && Boolean(organizationId) && Boolean(focusMonth),
        queryFn: () => getCloseChecklist(organizationId, focusMonth),
    });
    const closeNotesQuery = useQuery<AuditEventSummary[], Error>({
        queryKey: ["closeNotes", organizationId, focusMonth],
        enabled: hydrated && Boolean(organizationId) && Boolean(focusMonth),
        queryFn: () => listCloseNotes(organizationId, focusMonth),
    });
    const closeSignoffsQuery = useQuery<AuditEventSummary[], Error>({
        queryKey: ["closeSignoffs", organizationId, focusMonth],
        enabled: hydrated && Boolean(organizationId) && Boolean(focusMonth),
        queryFn: () => listCloseSignoffs(organizationId, focusMonth),
    });
    const attentionNotificationsQuery = useQuery<NotificationSummaryItem[], Error>({
        queryKey: ["attentionNotifications", organizationId],
        enabled: hydrated && Boolean(organizationId),
        queryFn: () => listAttentionNotifications(organizationId),
    });
    const deadLetterNotificationsQuery = useQuery<NotificationSummaryItem[], Error>({
        queryKey: ["deadLetterNotifications", organizationId],
        enabled: hydrated && Boolean(organizationId),
        queryFn: () => listDeadLetterNotifications(organizationId),
    });
    const settingsQuery = useQuery({
        queryKey: ["workspaceSettings", organizationId],
        enabled: hydrated && Boolean(organizationId),
        queryFn: () => getWorkspaceSettings(organizationId),
    });

    const loading =
        hydrated && organizationId
            ? dashboardQuery.isLoading ||
              reviewTasksQuery.isLoading ||
              reconciliationQuery.isLoading ||
              periodsQuery.isLoading ||
              attentionNotificationsQuery.isLoading ||
              deadLetterNotificationsQuery.isLoading ||
              settingsQuery.isLoading ||
              (Boolean(focusMonth) &&
                  (closeChecklistQuery.isLoading ||
                      closeNotesQuery.isLoading ||
                      closeSignoffsQuery.isLoading))
            : false;

    const queryError =
        dashboardQuery.error?.message ??
        reviewTasksQuery.error?.message ??
        reconciliationQuery.error?.message ??
        periodsQuery.error?.message ??
        attentionNotificationsQuery.error?.message ??
        deadLetterNotificationsQuery.error?.message ??
        settingsQuery.error?.message ??
        closeChecklistQuery.error?.message ??
        closeNotesQuery.error?.message ??
        closeSignoffsQuery.error?.message ??
        "";

    const reviewTasks = reviewTasksQuery.data ?? [];
    const unreconciledAccounts = reconciliationQuery.data?.unreconciledAccounts ?? [];
    const checklist = closeChecklistQuery.data ?? null;
    const closeNotes = closeNotesQuery.data ?? [];
    const closeSignoffs = closeSignoffsQuery.data ?? [];
    const attentionNotifications = attentionNotificationsQuery.data ?? [];
    const deadLetters = deadLetterNotificationsQuery.data ?? [];
    const workspaceSettings = settingsQuery.data ?? null;
    const currentPeriod =
        periodsQuery.data?.find((period) => period.periodStart.slice(0, 7) === focusMonth) ?? null;

    const incompleteChecklistItems = checklist?.items.filter((item) => !item.complete) ?? [];
    const blockerCount =
        reviewTasks.length + unreconciledAccounts.length + incompleteChecklistItems.length;
    const agingRiskCount =
        (dashboardQuery.data?.workflowInbox.overdueCount ?? 0) +
        attentionNotifications.length +
        deadLetters.length;
    const documentationCoverage = closeNotes.length + closeSignoffs.length;
    const overrideRisk = currentPeriod?.overrideReason ? 1 : 0;
    const reviewExposureAmount = reviewTasks.reduce((sum, task) => sum + Math.abs(task.amount ?? 0), 0);
    const materialityThreshold = workspaceSettings?.closeMaterialityThreshold ?? 500;
    const minimumCloseNotesRequired = workspaceSettings?.minimumCloseNotesRequired ?? 1;
    const requireSignoffBeforeClose = workspaceSettings?.requireSignoffBeforeClose ?? true;
    const minimumSignoffCount = workspaceSettings?.minimumSignoffCount ?? 1;
    const requireOwnerSignoffBeforeClose = workspaceSettings?.requireOwnerSignoffBeforeClose ?? false;
    const noteGap = Math.max(0, minimumCloseNotesRequired - closeNotes.length);
    const signoffGap = requireSignoffBeforeClose ? Math.max(0, minimumSignoffCount - closeSignoffs.length) : 0;
    const ownerSignoffRecorded = closeSignoffs.some((signoff) => signoff.actorUserId != null);
    const ownerSignoffGap = requireOwnerSignoffBeforeClose && !ownerSignoffRecorded ? 1 : 0;

    const readinessScore = useMemo(() => {
        const rawScore =
            100 -
            blockerCount * 12 -
            agingRiskCount * 7 -
            overrideRisk * 10 -
            noteGap * 6 -
            signoffGap * 10 -
            ownerSignoffGap * 8 -
            (reviewExposureAmount >= materialityThreshold ? 8 : 0) +
            (checklist?.closeReady ? 8 : 0);
        return clamp(rawScore, 12, 100);
    }, [
        agingRiskCount,
        blockerCount,
        checklist?.closeReady,
        materialityThreshold,
        noteGap,
        overrideRisk,
        ownerSignoffGap,
        reviewExposureAmount,
        signoffGap,
    ]);

    const decision: ReadinessDecision = useMemo(() => {
        if (currentPeriod?.status === "CLOSED") {
            return {
                state: "ALREADY_CLOSED",
                title: "This month is already closed",
                message: "The period is closed, so the remaining work is about review and traceability rather than making the final decision.",
                tone: "success",
                primaryHref: "/close",
                primaryLabel: "Review close record",
                secondaryHref: "/activity",
                secondaryLabel: "Open activity history",
            };
        }

        if (blockerCount > 0) {
            return {
                state: "WAIT_FOR_BLOCKERS",
                title: "Wait before closing",
                message: "There are still real bookkeeping blockers in motion. Closing now would push uncertainty into the next handoff instead of reducing it.",
                tone: "error",
                primaryHref: "/run-close",
                primaryLabel: "Resume guided close",
                secondaryHref: "/exceptions",
                secondaryLabel: "Review blockers",
            };
        }

        if (checklist?.closeReady && (signoffGap > 0 || ownerSignoffGap > 0)) {
            return {
                state: "NEEDS_SIGNOFF",
                title: "Ready in practice, waiting on sign-off",
                message: "The operational work is in good shape. The next best move is to record accountable approval before the month closes.",
                tone: "muted",
                primaryHref: "/close",
                primaryLabel: "Record sign-off",
                secondaryHref: "/activity",
                secondaryLabel: "Review close history",
            };
        }

        if (overrideRisk > 0 || agingRiskCount > 2 || reviewExposureAmount >= materialityThreshold) {
            return {
                state: "FORCE_CLOSE_WITH_CAUTION",
                title: "Only force-close with documented context",
                message: "Most of the month is contained, but the remaining operational signals or dollar exposure still deserve explicit owner awareness before any override path is used.",
                tone: "muted",
                primaryHref: "/close",
                primaryLabel: "Review close controls",
                secondaryHref: "/exceptions",
                secondaryLabel: "Inspect remaining risk",
            };
        }

        return {
            state: "READY_TO_CLOSE",
            title: "Ready to close",
            message: "The month is operationally clean, documented, and ready for a final close action when the owner is comfortable committing it.",
            tone: "success",
            primaryHref: "/close",
            primaryLabel: "Approve and close",
            secondaryHref: "/run-close",
            secondaryLabel: "Review runbook",
        };
    }, [
        agingRiskCount,
        blockerCount,
        checklist?.closeReady,
        currentPeriod?.status,
        overrideRisk,
        reviewExposureAmount,
        materialityThreshold,
        ownerSignoffGap,
        signoffGap,
    ]);

    const riskSignals = [
        {
            label: "Outstanding review tasks",
            value: `${reviewTasks.length}`,
            helper:
                reviewTasks.length === 0
                    ? "No ambiguous categorization decisions are still open."
                    : "Unresolved review work can still move ledger outcomes.",
        },
        {
            label: "Accounts still unreconciled",
            value: `${unreconciledAccounts.length}`,
            helper:
                unreconciledAccounts.length === 0
                    ? "Every active account is reconciled for the focus month."
                    : "Statement-backed evidence is still incomplete on at least one account.",
        },
        {
            label: "Operational delivery risk",
            value: `${attentionNotifications.length + deadLetters.length}`,
            helper:
                attentionNotifications.length + deadLetters.length === 0
                    ? "No delivery failures are currently threatening the handoff."
                    : "Attention notifications or dead letters still need operator awareness.",
        },
        {
            label: "Approval and notes coverage",
            value: `${documentationCoverage}`,
            helper:
                documentationCoverage === 0
                    ? "There is no note or approval trail yet for the focus month."
                    : "Close notes and sign-offs are building an audit-friendly handoff trail.",
        },
    ];

    if (!hydrated || loading) {
        return (
            <LoadingPanel
                title="Loading close readiness."
                message="Scoring close health, risk aging, and final approval posture for the focus month."
            />
        );
    }

    if (!organizationId || queryError) {
        return (
            <main className="p-4 sm:p-6 lg:p-8">
                <StatusBanner
                    tone="error"
                    title="Close readiness unavailable"
                    message={queryError || "No organization ID found. Please sign in again."}
                />
            </main>
        );
    }

    return (
        <main className="space-y-8 p-4 sm:p-6 lg:p-8">
            <PageHero
                eyebrow="Owner readiness"
                title="Close Readiness"
                description="See the month the way an owner, controller, or lead operator needs to see it: what still blocks close, how risky the remaining work feels, and whether the team should approve, wait, or force-close with caution."
                aside={
                    <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-1">
                        <SummaryMetric
                            label="Focus month"
                            value={focusMonth || "-"}
                            detail="The month currently under final review."
                        />
                        <SummaryMetric
                            label="Readiness score"
                            value={`${readinessScore}/100`}
                            detail="A blended signal from close blockers, aging risk, documentation, and sign-off posture."
                            tone={scoreTone(readinessScore)}
                        />
                        <SummaryMetric
                            label="Policy threshold"
                            value={`$${materialityThreshold}`}
                            detail={`Requires ${minimumCloseNotesRequired} close note(s), ${minimumSignoffCount} signoff(s)${requireOwnerSignoffBeforeClose ? ", including an owner," : ""} before the month feels complete.`}
                        />
                    </div>
                }
            >
                <div className="space-y-4">
                    <ProgressMeter
                        label="Documentation and approval coverage"
                        value={documentationCoverage}
                        total={Math.max(2, blockerCount + documentationCoverage)}
                        tone={documentationCoverage >= 2 ? "success" : "warning"}
                    />
                    <div className="flex flex-wrap gap-3">
                        <Link
                            href={decision.primaryHref}
                            className="rounded-md bg-emerald-300 px-4 py-2.5 text-sm font-semibold text-black hover:bg-emerald-200"
                        >
                            {decision.primaryLabel}
                        </Link>
                        <Link
                            href={decision.secondaryHref}
                            className="rounded-md border border-white/10 px-4 py-2.5 text-sm text-zinc-100 hover:bg-white/[0.05]"
                        >
                            {decision.secondaryLabel}
                        </Link>
                    </div>
                </div>
            </PageHero>

            <StatusBanner
                tone={decision.tone}
                title={decision.title}
                message={decision.message}
            />

            <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-5">
                <SummaryMetric
                    label="Open blockers"
                    value={`${blockerCount}`}
                    detail="Review tasks, unreconciled accounts, and incomplete close checklist items."
                    tone={riskTone(blockerCount)}
                />
                <SummaryMetric
                        label="Aging risk"
                        value={`${agingRiskCount}`}
                        detail="Overdue workflow pressure plus notification and delivery issues."
                        tone={riskTone(agingRiskCount)}
                    />
                <SummaryMetric
                    label="Review exposure"
                    value={`$${reviewExposureAmount.toFixed(2)}`}
                    detail="Open review task dollars compared with the workspace materiality threshold."
                    tone={riskTone(reviewExposureAmount >= materialityThreshold ? 1 : 0)}
                />
                <SummaryMetric
                    label="Close notes"
                    value={`${closeNotes.length}`}
                    detail={`Month-specific context already captured for the handoff. Target: ${minimumCloseNotesRequired}.`}
                    tone={riskTone(noteGap)}
                />
                <SummaryMetric
                    label="Recorded sign-offs"
                    value={`${closeSignoffs.length}`}
                    detail={requireSignoffBeforeClose ? `Formal approval is part of this workspace's close standard. Target: ${minimumSignoffCount}.` : "Formal approval is optional in this workspace."}
                    tone={riskTone(signoffGap + ownerSignoffGap)}
                />
                <SummaryMetric
                    label="Period posture"
                    value={currentPeriod?.status ?? "OPEN"}
                    detail={
                        currentPeriod?.closeMethod
                            ? `Current method: ${currentPeriod.closeMethod.toLowerCase()}.`
                            : "Still open and awaiting the final close decision."
                    }
                    tone={currentPeriod?.status === "CLOSED" ? "success" : "default"}
                />
            </div>

            <div className="grid gap-6 xl:grid-cols-[1.1fr_0.9fr]">
                <SectionBand
                    eyebrow="Executive summary"
                    title="Final pre-close review"
                    description="This is the fast read for deciding whether the team should keep working, capture approval, or proceed to close."
                >
                    <div className="grid gap-4 md:grid-cols-2">
                        {riskSignals.map((signal) => (
                            <div
                                key={signal.label}
                                className="rounded-lg border border-white/10 bg-white/[0.03] p-4"
                            >
                                <p className="text-xs font-medium uppercase tracking-[0.18em] text-zinc-500">
                                    {signal.label}
                                </p>
                                <p className="mt-3 text-2xl font-semibold text-white">{signal.value}</p>
                                <p className="mt-2 text-sm text-zinc-400">{signal.helper}</p>
                            </div>
                        ))}
                    </div>
                </SectionBand>

                <NextStepsList
                    title="Best next move"
                    items={[
                        blockerCount > 0
                            ? "Keep the month open until review tasks, reconciliations, and checklist blockers are finished."
                            : "The hard bookkeeping work is mostly complete, so the conversation can shift to documentation and accountability.",
                        noteGap > 0
                            ? `Add ${noteGap} more close note(s) so the handoff meets the workspace's documentation standard.`
                            : "Use the note trail to make the month understandable to anyone who did not live inside the work all week.",
                        signoffGap > 0 || ownerSignoffGap > 0
                            ? requireOwnerSignoffBeforeClose
                                ? "Capture the required signoffs, including at least one owner approval, before treating the month as fully settled."
                                : "Capture the required signoffs before treating the month as fully settled."
                            : "There is already approval history on the month, so the remaining decision is timing, not ownership.",
                        agingRiskCount > 0
                            ? "Clear the remaining delivery or overdue risk before the close becomes someone else’s surprise."
                            : "The operational lane is quiet, which is exactly the moment to finish close cleanly instead of waiting for new noise.",
                    ]}
                />
            </div>

            <SectionBand
                eyebrow="Why this score landed here"
                title="What is pushing readiness up or down"
                description="Use this breakdown when you need to explain the close posture to another operator, a manager, or yourself after a long day."
            >
                <div className="grid gap-4 lg:grid-cols-3">
                    <div className="rounded-lg border border-white/10 bg-white/[0.03] p-4">
                        <p className="text-sm font-semibold text-white">Helping confidence</p>
                        <ul className="mt-3 space-y-2 text-sm text-zinc-400">
                            <li>{checklist?.closeReady ? "Checklist is currently close-ready." : "Checklist still has open controls."}</li>
                            <li>{closeNotes.length > 0 ? `${closeNotes.length} close note(s) already captured against a target of ${minimumCloseNotesRequired}.` : "No close notes captured yet."}</li>
                            <li>{closeSignoffs.length > 0 ? `${closeSignoffs.length} sign-off(s) already recorded against a target of ${minimumSignoffCount}.` : requireSignoffBeforeClose ? "No sign-offs recorded yet." : "Sign-off is optional for this workspace."}</li>
                        </ul>
                    </div>
                    <div className="rounded-lg border border-white/10 bg-white/[0.03] p-4">
                        <p className="text-sm font-semibold text-white">Still creating risk</p>
                        <ul className="mt-3 space-y-2 text-sm text-zinc-400">
                            <li>{reviewTasks.length} review task(s) still open.</li>
                            <li>${reviewExposureAmount.toFixed(2)} of open review exposure against a ${materialityThreshold} threshold.</li>
                            <li>{unreconciledAccounts.length} account(s) still unreconciled.</li>
                            <li>{incompleteChecklistItems.length} checklist item(s) still incomplete.</li>
                            {requireOwnerSignoffBeforeClose ? <li>Owner sign-off is required before standard close.</li> : null}
                        </ul>
                    </div>
                    <div className="rounded-lg border border-white/10 bg-white/[0.03] p-4">
                        <p className="text-sm font-semibold text-white">Operational watchlist</p>
                        <ul className="mt-3 space-y-2 text-sm text-zinc-400">
                            <li>{dashboardQuery.data?.workflowInbox.overdueCount ?? 0} overdue workflow item(s).</li>
                            <li>{attentionNotifications.length} attention notification(s).</li>
                            <li>{deadLetters.length} dead-letter issue(s) requiring intervention.</li>
                        </ul>
                    </div>
                </div>
            </SectionBand>
        </main>
    );
}
