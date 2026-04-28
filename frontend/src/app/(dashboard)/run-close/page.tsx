"use client";

import { useMemo } from "react";
import { useQuery } from "@tanstack/react-query";
import Link from "next/link";
import {
    LoadingPanel,
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

type RunbookStep = {
    id: string;
    title: string;
    description: string;
    href: string;
    cta: string;
    complete: boolean;
    detail: string;
};

export default function RunClosePage() {
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
    const closeChecklistQuery = useQuery<CloseChecklistSummary, Error>({
        queryKey: ["closeChecklist", organizationId, focusMonth],
        enabled: hydrated && Boolean(organizationId) && Boolean(focusMonth),
        queryFn: () => getCloseChecklist(organizationId, focusMonth),
    });
    const periodsQuery = useQuery<AccountingPeriodSummary[], Error>({
        queryKey: ["accountingPeriods", organizationId],
        enabled: hydrated && Boolean(organizationId),
        queryFn: () => listAccountingPeriods(organizationId),
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

    const loading =
        hydrated && organizationId
            ? dashboardQuery.isLoading ||
              reviewTasksQuery.isLoading ||
              reconciliationQuery.isLoading ||
              periodsQuery.isLoading ||
              attentionNotificationsQuery.isLoading ||
              deadLetterNotificationsQuery.isLoading ||
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
    const currentPeriod =
        periodsQuery.data?.find((period) => period.periodStart.slice(0, 7) === focusMonth) ?? null;

    const steps: RunbookStep[] = useMemo(() => {
        const exceptionCount = attentionNotifications.length + deadLetters.length;
        const notesAndAdjustmentsComplete = closeNotes.length > 0;
        const closeApproved = closeSignoffs.length > 0;
        const periodClosed = currentPeriod?.status === "CLOSED";

        return [
            {
                id: "review",
                title: "Resolve review queue decisions",
                description: "Clear ambiguous transactions before they ripple into reconciliation and close decisions.",
                href: "/review-queue",
                cta: "Open review queue",
                complete: reviewTasks.length === 0,
                detail:
                    reviewTasks.length === 0
                        ? "No review tasks are still blocking the month."
                        : `${reviewTasks.length} task(s) still need a final category.`,
            },
            {
                id: "reconcile",
                title: "Finish reconciliations",
                description: "Get statement-backed confidence on every account that still holds the month open.",
                href: "/reconciliation",
                cta: "Open reconciliation",
                complete: unreconciledAccounts.length === 0,
                detail:
                    unreconciledAccounts.length === 0
                        ? "All active accounts are reconciled for the focus month."
                        : `${unreconciledAccounts.length} account(s) still need reconciliation work.`,
            },
            {
                id: "exceptions",
                title: "Clear delivery and exception risk",
                description: "Work through dead letters, attention notifications, and anything else that will surprise people during handoff.",
                href: "/exceptions",
                cta: "Open exceptions",
                complete: exceptionCount === 0,
                detail:
                    exceptionCount === 0
                        ? "No operational delivery issues are currently blocking a clean handoff."
                        : `${exceptionCount} notification or delivery issue(s) still need intervention.`,
            },
            {
                id: "document",
                title: "Post adjustments and leave notes",
                description: "Document what changed so the month tells a coherent story even after context fades.",
                href: "/close",
                cta: "Open close workspace",
                complete: notesAndAdjustmentsComplete,
                detail:
                    notesAndAdjustmentsComplete
                        ? `${closeNotes.length} close note(s) captured for this month.`
                        : "No close notes have been recorded yet for the focus month.",
            },
            {
                id: "approve",
                title: "Approve and close the month",
                description: "Record formal sign-off, then close the period when the team is ready to commit to the final state.",
                href: "/close",
                cta: "Approve and close",
                complete: closeApproved || periodClosed,
                detail: periodClosed
                    ? `The period is already closed via ${currentPeriod?.closeMethod?.toLowerCase() ?? "unknown"} controls.`
                    : closeApproved
                      ? `${closeSignoffs.length} sign-off(s) recorded for the focus month.`
                      : "No formal close approval has been recorded yet.",
            },
        ];
    }, [
        attentionNotifications.length,
        closeNotes.length,
        closeSignoffs.length,
        currentPeriod?.closeMethod,
        currentPeriod?.status,
        deadLetters.length,
        reviewTasks.length,
        unreconciledAccounts.length,
    ]);

    const completedSteps = steps.filter((step) => step.complete).length;
    const nextStep = steps.find((step) => !step.complete) ?? null;

    if (!hydrated || loading) {
        return (
            <LoadingPanel
                title="Loading close runbook."
                message="Sequencing the month-end work so the next action is obvious."
            />
        );
    }

    if (!organizationId || queryError) {
        return (
            <main className="p-4 sm:p-6 lg:p-8">
                <StatusBanner
                    tone="error"
                    title="Close runbook unavailable"
                    message={queryError || "No organization ID found. Please sign in again."}
                />
            </main>
        );
    }

    return (
        <main className="space-y-8 p-4 sm:p-6 lg:p-8">
            <PageHero
                eyebrow="Guided month-end"
                title="Run Close"
                description="Move through the month in a clean sequence: resolve ambiguity, reconcile, clear exceptions, document adjustments, then approve and close."
                aside={
                    <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-1">
                        <SummaryMetric
                            label="Focus month"
                            value={focusMonth || "-"}
                            detail="The month currently being guided through close."
                        />
                        <SummaryMetric
                            label="Next step"
                            value={nextStep?.title ?? "Close sequence complete"}
                            detail={nextStep?.detail ?? "The runbook is clear across every stage."}
                            tone={nextStep ? "warning" : "success"}
                        />
                    </div>
                }
            >
                <div className="space-y-4">
                    <ProgressMeter
                        label="Close runbook progress"
                        value={completedSteps}
                        total={steps.length}
                        tone={completedSteps === steps.length ? "success" : "warning"}
                    />
                    <div className="flex flex-wrap gap-3">
                        {nextStep ? (
                            <Link
                                href={nextStep.href}
                                className="rounded-md bg-emerald-300 px-4 py-2.5 text-sm font-semibold text-black hover:bg-emerald-200"
                            >
                                {nextStep.cta}
                            </Link>
                        ) : null}
                        <Link
                            href="/exceptions"
                            className="rounded-md border border-white/10 px-4 py-2.5 text-sm text-zinc-100 hover:bg-white/[0.05]"
                        >
                            Open exception lane
                        </Link>
                        <Link
                            href="/readiness"
                            className="rounded-md border border-white/10 px-4 py-2.5 text-sm text-zinc-100 hover:bg-white/[0.05]"
                        >
                            Review close readiness
                        </Link>
                    </div>
                </div>
            </PageHero>

            <SectionBand
                eyebrow="Sequence"
                title="Run the month in order"
                description="Each step is designed to reduce downstream noise. Finishing them in sequence keeps close work from becoming circular."
            >
                <div className="space-y-4">
                    {steps.map((step, index) => (
                        <div
                            key={step.id}
                            className="rounded-lg border border-white/10 bg-white/[0.03] p-5"
                        >
                            <div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
                                <div className="space-y-2">
                                    <div className="flex flex-wrap items-center gap-3">
                                        <span className="flex h-7 w-7 items-center justify-center rounded-full border border-white/10 bg-white/[0.04] text-xs text-zinc-300">
                                            {index + 1}
                                        </span>
                                        <h2 className="text-lg font-semibold text-white">{step.title}</h2>
                                        <span
                                            className={[
                                                "rounded-full px-3 py-1 text-xs font-medium",
                                                step.complete
                                                    ? "border border-emerald-400/30 bg-emerald-300/10 text-emerald-100"
                                                    : "border border-amber-400/30 bg-amber-300/10 text-amber-100",
                                            ].join(" ")}
                                        >
                                            {step.complete ? "Complete" : "Needs action"}
                                        </span>
                                    </div>
                                    <p className="text-sm text-zinc-400">{step.description}</p>
                                    <p className="text-sm text-zinc-300">{step.detail}</p>
                                </div>
                                <Link
                                    href={step.href}
                                    className="rounded-md border border-white/10 px-4 py-2.5 text-sm text-zinc-100 hover:bg-white/[0.05]"
                                >
                                    {step.cta}
                                </Link>
                            </div>
                        </div>
                    ))}
                </div>
            </SectionBand>

            {checklist?.closeReady ? (
                <StatusBanner
                    tone="success"
                    title="Checklist-driven close is ready"
                    message="The checklist is green for the focus month. The remaining judgment calls are now mostly about documentation, approval, and timing."
                />
            ) : (
                <StatusBanner
                    tone="muted"
                    title="The close is still in motion"
                    message="That’s normal. Use the sequence above to keep the team moving in one direction instead of bouncing between pages."
                />
            )}
        </main>
    );
}
