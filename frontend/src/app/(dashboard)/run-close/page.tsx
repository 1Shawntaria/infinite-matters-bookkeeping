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
    CloseAttestation,
    ClosePlaybookItem,
    CloseChecklistSummary,
    getCloseAttestation,
    getCloseChecklist,
    listClosePlaybookItems,
    listAccountingPeriods,
    listCloseNotes,
    listCloseSignoffs,
} from "@/lib/api/close";
import { listAttentionNotifications, listDeadLetterNotifications } from "@/lib/api/notifications";
import { useOrganizationSession } from "@/lib/auth/session";
import { AuditEventSummary } from "@/lib/api/audit";
import { NotificationSummaryItem } from "@/lib/api/auth";
import { getWorkspaceSettings, listCloseTemplateItems } from "@/lib/api/settings";

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
    const closeAttestationQuery = useQuery<CloseAttestation, Error>({
        queryKey: ["closeAttestation", organizationId, focusMonth],
        enabled: hydrated && Boolean(organizationId) && Boolean(focusMonth),
        queryFn: () => getCloseAttestation(organizationId, focusMonth),
    });
    const settingsQuery = useQuery({
        queryKey: ["workspaceSettings", organizationId],
        enabled: hydrated && Boolean(organizationId),
        queryFn: () => getWorkspaceSettings(organizationId),
    });
    const closeTemplateItemsQuery = useQuery({
        queryKey: ["closeTemplateItems", organizationId],
        enabled: hydrated && Boolean(organizationId),
        queryFn: () => listCloseTemplateItems(organizationId),
    });
    const closePlaybookQuery = useQuery<ClosePlaybookItem[], Error>({
        queryKey: ["closePlaybookItems", organizationId, focusMonth],
        enabled: hydrated && Boolean(organizationId) && Boolean(focusMonth),
        queryFn: () => listClosePlaybookItems(organizationId, focusMonth),
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
              closeTemplateItemsQuery.isLoading ||
              closePlaybookQuery.isLoading ||
              (Boolean(focusMonth) &&
                  (closeChecklistQuery.isLoading ||
                      closeNotesQuery.isLoading ||
                      closeSignoffsQuery.isLoading ||
                      closeAttestationQuery.isLoading))
            : false;

    const queryError =
        dashboardQuery.error?.message ??
        reviewTasksQuery.error?.message ??
        reconciliationQuery.error?.message ??
        periodsQuery.error?.message ??
        attentionNotificationsQuery.error?.message ??
        deadLetterNotificationsQuery.error?.message ??
        settingsQuery.error?.message ??
        closeTemplateItemsQuery.error?.message ??
        closePlaybookQuery.error?.message ??
        closeChecklistQuery.error?.message ??
        closeNotesQuery.error?.message ??
        closeAttestationQuery.error?.message ??
        closeSignoffsQuery.error?.message ??
        "";

    const reviewTasks = reviewTasksQuery.data ?? [];
    const unreconciledAccounts = reconciliationQuery.data?.unreconciledAccounts ?? [];
    const checklist = closeChecklistQuery.data ?? null;
    const closeNotes = closeNotesQuery.data ?? [];
    const closeSignoffs = closeSignoffsQuery.data ?? [];
    const closeAttestation = closeAttestationQuery.data ?? null;
    const attentionNotifications = attentionNotificationsQuery.data ?? [];
    const deadLetters = deadLetterNotificationsQuery.data ?? [];
    const workspaceSettings = settingsQuery.data ?? null;
    const closeTemplateItems = closeTemplateItemsQuery.data ?? [];
    const closePlaybookItems = useMemo(
        () => closePlaybookQuery.data ?? [],
        [closePlaybookQuery.data]
    );
    const currentPeriod =
        periodsQuery.data?.find((period) => period.periodStart.slice(0, 7) === focusMonth) ?? null;
    const attestationRoutingReady =
        Boolean(closeAttestation?.closeOwner?.id) &&
        Boolean(closeAttestation?.closeApprover?.id) &&
        closeAttestation?.closeOwner?.id !== closeAttestation?.closeApprover?.id;

    const steps: RunbookStep[] = useMemo(() => {
        const exceptionCount = attentionNotifications.length + deadLetters.length;
        const pendingPlaybookItems = closePlaybookItems.filter((item) => !item.satisfied).length;
        const notesAndAdjustmentsComplete = closeNotes.length > 0 && pendingPlaybookItems === 0;
        const closeApproved = closeSignoffs.length > 0 && Boolean(closeAttestation?.attested);
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
                        ? `${closeNotes.length} close note(s) captured and recurring checks are satisfied.`
                        : pendingPlaybookItems > 0
                          ? `${pendingPlaybookItems} recurring playbook item(s) still need completion or approval.`
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
                      ? `${closeSignoffs.length} sign-off(s) recorded and the month-level attestation is confirmed.`
                      : !attestationRoutingReady
                        ? closeAttestation?.closeOwner?.id && closeAttestation?.closeApprover?.id
                            ? "Formal sign-off can wait, but the close owner and approver must be different people before month-level attestation can clear."
                            : "Formal sign-off can wait, but the month still needs a named owner and a separate approver before attestation can clear."
                      : closeSignoffs.length > 0
                        ? closeAttestation?.closeApprover
                            ? `Formal sign-off exists, but ${closeAttestation.closeApprover.fullName} still needs to confirm the month-level attestation.`
                            : "Formal sign-off exists, but the month-level attestation still needs confirmation."
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
        closePlaybookItems,
        closeAttestation?.attested,
        attestationRoutingReady,
        closeAttestation?.closeApprover,
        closeAttestation?.closeOwner,
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
                {!attestationRoutingReady && focusMonth ? (
                    <div className="mb-4">
                        <StatusBanner
                            tone="error"
                            title="Attestation routing still needs separation of duties"
                            message={
                                closeAttestation?.closeOwner?.id && closeAttestation?.closeApprover?.id
                                    ? "The current close owner and approver are the same person. Assign a different approver in the close workspace before expecting the final attestation step to clear."
                                    : "Assign a close owner and a separate approver in the close workspace before expecting the final attestation step to clear."
                            }
                        />
                    </div>
                ) : null}
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

            <SectionBand
                eyebrow="Workspace standard"
                title="Standing close playbook"
                description="These are the recurring checks and approval expectations your team wants visible every month, even when the specific transactions change."
            >
                <div className="grid gap-4 xl:grid-cols-[1.05fr_0.95fr]">
                    <div className="space-y-3">
                        {closeTemplateItems.length === 0 ? (
                            <StatusBanner
                                tone="muted"
                                title="No recurring close playbook items yet"
                                message="Add them in workspace settings when your team is ready to formalize the month-end standard."
                            />
                        ) : (
                            closePlaybookItems.map((item) => (
                                <div key={item.id} className="rounded-lg border border-white/10 bg-white/[0.03] p-4">
                                    <div className="flex flex-wrap items-center gap-3">
                                        <p className="text-sm font-semibold text-white">{item.sortOrder}. {item.label}</p>
                                        <span className={[
                                            "rounded-full px-2.5 py-1 text-[11px] font-medium",
                                            item.satisfied
                                                ? "border border-emerald-400/30 bg-emerald-300/10 text-emerald-100"
                                                : "border border-amber-400/30 bg-amber-300/10 text-amber-100",
                                        ].join(" ")}>
                                            {item.satisfied ? "Satisfied" : item.completed ? "Awaiting approval" : "Open"}
                                        </span>
                                    </div>
                                    <p className="mt-2 text-sm leading-6 text-zinc-400">{item.guidance}</p>
                                    <p className="mt-3 text-xs text-zinc-500">
                                        {item.assignee ? `Assignee: ${item.assignee.fullName}. ` : "No assignee yet. "}
                                        {item.approver ? `Approver: ${item.approver.fullName}. ` : "No approver assigned. "}
                                        {item.completed ? `Completed${item.completedBy ? ` by ${item.completedBy.fullName}` : ""}. ` : ""}
                                        {item.approved ? `Approved${item.approvedBy ? ` by ${item.approvedBy.fullName}` : ""}.` : ""}
                                    </p>
                                </div>
                            ))
                        )}
                    </div>

                    <NextStepsList
                        title="Approval policy"
                        items={[
                            `Document at least ${workspaceSettings?.minimumCloseNotesRequired ?? 1} close note(s) for the month.`,
                            workspaceSettings?.requireSignoffBeforeClose
                                ? `Record at least ${workspaceSettings?.minimumSignoffCount ?? 1} signoff(s) before standard close.`
                                : "Formal signoff is optional before standard close in this workspace.",
                            workspaceSettings?.requireOwnerSignoffBeforeClose
                                ? "At least one of those signoffs must come from an owner."
                                : "Owner-specific signoff is not required by policy.",
                            workspaceSettings?.requireTemplateCompletionBeforeClose
                                ? "Recurring close playbook items must be completed before standard close."
                                : "Recurring close playbook items inform the runbook, but do not block standard close.",
                            `Treat unresolved review exposure above $${workspaceSettings?.closeMaterialityThreshold ?? 500} as materially cautionary.`,
                        ]}
                    />
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
