"use client";

import { useQuery } from "@tanstack/react-query";
import Link from "next/link";
import {
    LoadingPanel,
    NextStepsList,
    PageHero,
    SectionBand,
    StatusBanner,
    SummaryMetric,
} from "@/components/app-surfaces";
import { getDashboardSnapshot, DashboardSnapshot } from "@/lib/api/dashboard";
import { listAttentionNotifications, listDeadLetterNotifications } from "@/lib/api/notifications";
import { getReconciliationDashboard, ReconciliationDashboard } from "@/lib/api/reconciliation";
import { getReviewTasks, ReviewTask } from "@/lib/api/reviews";
import {
    AccountingPeriodSummary,
    CloseChecklistSummary,
    getCloseChecklist,
    listAccountingPeriods,
} from "@/lib/api/close";
import { useOrganizationSession } from "@/lib/auth/session";
import { NotificationSummaryItem } from "@/lib/api/auth";

function toneForCount(count: number) {
    return count > 0 ? "warning" : "success";
}

export default function ExceptionsPage() {
    const { organizationId, hydrated } = useOrganizationSession();

    const dashboardQuery = useQuery<DashboardSnapshot, Error>({
        queryKey: ["dashboardSnapshot", organizationId],
        enabled: hydrated && Boolean(organizationId),
        queryFn: () => getDashboardSnapshot(organizationId),
    });
    const periodsQuery = useQuery<AccountingPeriodSummary[], Error>({
        queryKey: ["accountingPeriods", organizationId],
        enabled: hydrated && Boolean(organizationId),
        queryFn: () => listAccountingPeriods(organizationId),
    });
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

    const focusMonth = dashboardQuery.data?.focusMonth ?? "";
    const closeChecklistQuery = useQuery<CloseChecklistSummary, Error>({
        queryKey: ["closeChecklist", organizationId, focusMonth],
        enabled: hydrated && Boolean(organizationId) && Boolean(focusMonth),
        queryFn: () => getCloseChecklist(organizationId, focusMonth),
    });

    const loading =
        hydrated && organizationId
            ? dashboardQuery.isLoading ||
              periodsQuery.isLoading ||
              reviewTasksQuery.isLoading ||
              reconciliationQuery.isLoading ||
              attentionNotificationsQuery.isLoading ||
              deadLetterNotificationsQuery.isLoading ||
              (Boolean(focusMonth) && closeChecklistQuery.isLoading)
            : false;

    const queryError =
        dashboardQuery.error?.message ??
        periodsQuery.error?.message ??
        reviewTasksQuery.error?.message ??
        reconciliationQuery.error?.message ??
        attentionNotificationsQuery.error?.message ??
        deadLetterNotificationsQuery.error?.message ??
        closeChecklistQuery.error?.message ??
        "";

    const currentPeriod =
        periodsQuery.data?.find((period) => period.periodStart.slice(0, 7) === focusMonth) ?? null;
    const reviewTasks = reviewTasksQuery.data ?? [];
    const unreconciledAccounts = reconciliationQuery.data?.unreconciledAccounts ?? [];
    const checklistItems = closeChecklistQuery.data?.items ?? [];
    const blockingChecklistItems = checklistItems.filter((item) => !item.complete);
    const attentionNotifications = attentionNotificationsQuery.data ?? [];
    const deadLetters = deadLetterNotificationsQuery.data ?? [];

    const groupedExceptionCount =
        reviewTasks.length +
        unreconciledAccounts.length +
        blockingChecklistItems.length +
        attentionNotifications.length +
        deadLetters.length;

    const topBlockers = [
        ...reviewTasks.slice(0, 2).map((task) => ({
            label: task.title,
            reason: task.description,
            href: "/review-queue",
        })),
        ...unreconciledAccounts.slice(0, 2).map((account) => ({
            label: account.accountName,
            reason: account.actionReason,
            href: account.actionPath,
        })),
        ...deadLetters.slice(0, 2).map((item) => ({
            label: item.message,
            reason: item.lastError || "Delivery is waiting on manual intervention.",
            href: "/notifications",
        })),
    ].slice(0, 5);

    if (!hydrated || loading) {
        return (
            <LoadingPanel
                title="Loading exceptions workspace."
                message="Collecting review blockers, reconciliation issues, and delivery failures into one close-risk view."
            />
        );
    }

    if (!organizationId || queryError) {
        return (
            <main className="p-4 sm:p-6 lg:p-8">
                <StatusBanner
                    tone="error"
                    title="Exceptions workspace unavailable"
                    message={queryError || "No organization ID found. Please sign in again."}
                />
            </main>
        );
    }

    return (
        <main className="space-y-8 p-4 sm:p-6 lg:p-8">
            <PageHero
                eyebrow="Month-end exception lane"
                title="Exceptions"
                description="See every issue that still blocks a calm, clean close, grouped by the kind of action it needs instead of scattered across the app."
                aside={
                    <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-1">
                        <SummaryMetric
                            label="Focus month"
                            value={focusMonth || "-"}
                            detail="The month currently under operational review."
                        />
                        <SummaryMetric
                            label="Active blockers"
                            value={`${groupedExceptionCount}`}
                            detail="Review tasks, reconciliation gaps, checklist blockers, and delivery issues still needing action."
                            tone={toneForCount(groupedExceptionCount)}
                        />
                    </div>
                }
            >
                <div className="flex flex-wrap gap-3">
                    <Link
                        href="/close"
                        className="rounded-md bg-emerald-300 px-4 py-2.5 text-sm font-semibold text-black hover:bg-emerald-200"
                    >
                        Open close workspace
                    </Link>
                    <Link
                        href="/dashboard"
                        className="rounded-md border border-white/10 px-4 py-2.5 text-sm text-zinc-100 hover:bg-white/[0.05]"
                    >
                        Back to dashboard
                    </Link>
                </div>
            </PageHero>

            <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-5">
                <SummaryMetric
                    label="Review blockers"
                    value={`${reviewTasks.length}`}
                    detail="Transactions still waiting on a final category."
                    tone={toneForCount(reviewTasks.length)}
                />
                <SummaryMetric
                    label="Reconciliation gaps"
                    value={`${unreconciledAccounts.length}`}
                    detail="Accounts not fully reconciled for the focus month."
                    tone={toneForCount(unreconciledAccounts.length)}
                />
                <SummaryMetric
                    label="Checklist blockers"
                    value={`${blockingChecklistItems.length}`}
                    detail="Controls that still prevent a clean checklist-driven close."
                    tone={toneForCount(blockingChecklistItems.length)}
                />
                <SummaryMetric
                    label="Attention notifications"
                    value={`${attentionNotifications.length}`}
                    detail="Operational sends already flagged as risky."
                    tone={toneForCount(attentionNotifications.length)}
                />
                <SummaryMetric
                    label="Dead letters"
                    value={`${deadLetters.length}`}
                    detail="Messages that need operator intervention before they can move again."
                    tone={toneForCount(deadLetters.length)}
                />
            </div>

            {currentPeriod?.overrideReason ? (
                <StatusBanner
                    tone="muted"
                    title="This month includes an override history"
                    message={`Latest override reason: ${currentPeriod.overrideReason}`}
                />
            ) : null}

            <div className="grid gap-6 xl:grid-cols-[1.05fr_0.95fr]">
                <SectionBand
                    eyebrow="Priority view"
                    title="Start here"
                    description="These are the items most likely to keep the month from feeling truly finished."
                >
                    {topBlockers.length === 0 ? (
                        <StatusBanner
                            tone="success"
                            title="No active exceptions right now"
                            message="The close lane is clear across review, reconciliation, notifications, and checklist controls."
                        />
                    ) : (
                        <div className="space-y-3">
                            {topBlockers.map((item) => (
                                <Link
                                    key={`${item.href}-${item.label}`}
                                    href={item.href}
                                    className="block rounded-lg border border-white/10 bg-white/[0.03] p-4 hover:border-white/20"
                                >
                                    <p className="text-sm font-semibold text-white">{item.label}</p>
                                    <p className="mt-1 text-sm text-zinc-400">{item.reason}</p>
                                </Link>
                            ))}
                        </div>
                    )}
                </SectionBand>

                <NextStepsList
                    title="A sane month-end order"
                    items={[
                        "Resolve ambiguous review tasks before expecting reconciliation numbers to settle.",
                        "Complete account reconciliations before treating close blockers as purely procedural.",
                        "Clear delivery failures and dead letters before handoff so owners are not surprised by silent misses.",
                        "Use the close workspace last to post adjustments, document context, and approve the month once the real blockers are gone.",
                    ]}
                />
            </div>

            <SectionBand
                eyebrow="Review queue"
                title="Transactions still waiting on judgment"
                description="These are categorization decisions that will leak uncertainty into close, reconciliation, and reporting until somebody resolves them."
            >
                {reviewTasks.length === 0 ? (
                    <StatusBanner
                        tone="success"
                        title="No review blockers"
                        message="The review queue is clear for the current workspace."
                    />
                ) : (
                    <div className="space-y-3">
                        {reviewTasks.map((task) => (
                            <div
                                key={task.taskId}
                                className="flex flex-wrap items-center justify-between gap-3 rounded-lg border border-white/10 bg-white/[0.03] p-4"
                            >
                                <div>
                                    <p className="text-sm font-semibold text-white">{task.merchant}</p>
                                    <p className="mt-1 text-sm text-zinc-400">{task.description}</p>
                                </div>
                                <Link
                                    href="/review-queue"
                                    className="rounded-md border border-white/10 px-3 py-2 text-sm text-zinc-100 hover:bg-white/[0.05]"
                                >
                                    Open review queue
                                </Link>
                            </div>
                        ))}
                    </div>
                )}
            </SectionBand>

            <SectionBand
                eyebrow="Reconciliation"
                title="Accounts still holding the month open"
                description="Unreconciled accounts deserve their own lane because they create the kind of close anxiety that spreads fast."
            >
                {unreconciledAccounts.length === 0 ? (
                    <StatusBanner
                        tone="success"
                        title="No reconciliation blockers"
                        message="Every active account is currently reconciled for the focus month."
                    />
                ) : (
                    <div className="space-y-3">
                        {unreconciledAccounts.map((account) => (
                            <Link
                                key={account.accountId}
                                href={account.actionPath}
                                className="block rounded-lg border border-white/10 bg-white/[0.03] p-4 hover:border-white/20"
                            >
                                <p className="text-sm font-semibold text-white">{account.accountName}</p>
                                <p className="mt-1 text-sm text-zinc-400">{account.actionReason}</p>
                            </Link>
                        ))}
                    </div>
                )}
            </SectionBand>

            <div className="grid gap-6 xl:grid-cols-2">
                <SectionBand
                    eyebrow="Checklist"
                    title="Controls still blocking a clean close"
                    description="These are the procedural gates still marked incomplete for the focus month."
                >
                    {blockingChecklistItems.length === 0 ? (
                        <StatusBanner
                            tone="success"
                            title="Checklist blockers cleared"
                            message="The focus month currently has no unresolved checklist items."
                        />
                    ) : (
                        <div className="space-y-3">
                            {blockingChecklistItems.map((item) => (
                                <div
                                    key={item.label}
                                    className="rounded-lg border border-white/10 bg-white/[0.03] p-4"
                                >
                                    <p className="text-sm font-semibold text-white">{item.label}</p>
                                    <p className="mt-1 text-sm text-zinc-400">{item.detail}</p>
                                </div>
                            ))}
                        </div>
                    )}
                </SectionBand>

                <SectionBand
                    eyebrow="Delivery operations"
                    title="Notifications that still need a human"
                    description="This is the operational cleanup lane for delivery failures and dead letters that should not survive into handoff."
                >
                    {attentionNotifications.length === 0 && deadLetters.length === 0 ? (
                        <StatusBanner
                            tone="success"
                            title="No delivery exceptions"
                            message="Notification operations are currently clear for this workspace."
                        />
                    ) : (
                        <div className="space-y-3">
                            {[...attentionNotifications, ...deadLetters].map((item) => (
                                <Link
                                    key={item.id}
                                    href="/notifications"
                                    className="block rounded-lg border border-white/10 bg-white/[0.03] p-4 hover:border-white/20"
                                >
                                    <p className="text-sm font-semibold text-white">{item.message}</p>
                                    <p className="mt-1 text-sm text-zinc-400">
                                        {item.lastError || item.deliveryState}
                                    </p>
                                </Link>
                            ))}
                        </div>
                    )}
                </SectionBand>
            </div>
        </main>
    );
}
