"use client";

import { useQuery } from "@tanstack/react-query";
import { useState } from "react";
import {
    getDashboardSnapshot,
    DashboardSnapshot,
} from "@/lib/api/dashboard";
import Link from "next/link";
import { mapBackendActionPathToFrontend } from "@/lib/navigation";
import { useOrganizationSession } from "@/lib/auth/session";
import {
    LoadingPanel,
    NextStepsList,
    PageHero,
    ProgressMeter,
    SectionBand,
    StatusBanner,
    SummaryMetric,
} from "@/components/app-surfaces";

export default function DashboardPage() {
    const { organizationId, hydrated } = useOrganizationSession();
    const [error, setError] = useState("");
    const dashboardQuery = useQuery<DashboardSnapshot, Error>({
        queryKey: ["dashboardSnapshot", organizationId],
        enabled: hydrated && Boolean(organizationId),
        queryFn: async () => {
            const result = await getDashboardSnapshot(organizationId);
            setError("");
            return result;
        },
    });
    const data = organizationId ? (dashboardQuery.data ?? null) : null;
    const loading = hydrated && organizationId ? dashboardQuery.isLoading : false;
    const queryError = dashboardQuery.error?.message ?? "";
    const actionHref = data?.primaryAction
        ? mapBackendActionPathToFrontend(data.primaryAction.actionPath)
        : null;
    const expenseCategories = Array.isArray(data?.expenseCategories)
        ? data.expenseCategories
        : [];
    const workflowCounts = data?.workflowInbox ?? {
        openCount: 0,
        overdueCount: 0,
        dueTodayCount: 0,
        highPriorityCount: 0,
        unassignedCount: 0,
        assignedToCurrentUserCount: 0,
    };
    const reviewTaskPressure = workflowCounts.openCount + (data?.period?.unreconciledAccountCount ?? 0);
    const closeCompletionDenominator = Math.max(
        1,
        (data?.period?.unreconciledAccountCount ?? 0) + 1
    );
    const closeCompletionValue = data?.period?.closeReady ? closeCompletionDenominator : 1;
    const lowDataWorkspace =
        (data?.postedTransactionCount ?? 0) === 0 &&
        workflowCounts.openCount === 0 &&
        (data?.period?.unreconciledAccountCount ?? 0) === 0 &&
        expenseCategories.length === 0;

    if (!hydrated || loading) {
        return (
            <LoadingPanel
                title="Loading dashboard."
                message="Pulling cash position, workflow activity, and close readiness into one view."
            />
        );
    }

    if (!organizationId || error || queryError) {
        return (
            <main className="p-4 sm:p-6 lg:p-8">
                <StatusBanner
                    tone="error"
                    title="Dashboard unavailable"
                    message={error || queryError || "No organization ID found. Please sign in again."}
                />
            </main>
        );
    }

    return (
        <main className="space-y-8 p-4 sm:p-6 lg:p-8">
            <PageHero
                eyebrow="Workspace snapshot"
                title="Dashboard"
                description="Monitor cash position, review pressure, and close readiness without hunting through separate screens."
                aside={
                    <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-1">
                        <SummaryMetric
                            label="Focus Month"
                            value={data?.focusMonth ?? "-"}
                            detail="The period currently driving close-readiness work."
                        />
                        <SummaryMetric
                            label="Recommended next"
                            value={data?.primaryAction?.label ?? "No urgent action right now"}
                            detail={data?.primaryAction?.reason ?? "The workspace is relatively steady right now."}
                            tone={data?.primaryAction ? "warning" : "success"}
                        />
                    </div>
                }
            >
                {actionHref ? (
                    <div className="flex flex-wrap items-center gap-3">
                        <Link
                            href={actionHref}
                            className="rounded-md bg-emerald-300 px-4 py-2.5 text-sm font-semibold text-black hover:bg-emerald-200"
                        >
                            {data?.primaryAction?.label}
                        </Link>
                        <p className="text-sm text-zinc-300">
                            {data?.primaryAction?.urgency
                                ? `Urgency: ${data.primaryAction.urgency}`
                                : "No urgent blockers detected."}
                        </p>
                    </div>
                ) : null}
            </PageHero>

            <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
                <SummaryMetric
                    label="Cash Balance"
                    value={`$${Number(data?.cashBalance ?? 0).toFixed(2)}`}
                    detail="Current ledger-backed cash position."
                />
                <SummaryMetric
                    label="Posted Transactions"
                    value={`${data?.postedTransactionCount ?? 0}`}
                    detail="Items already posted into the books."
                />
                <SummaryMetric
                    label="Open Tasks"
                    value={`${data?.workflowInbox?.openCount ?? 0}`}
                    detail={`${data?.workflowInbox?.highPriorityCount ?? 0} high-priority tasks still need attention.`}
                    tone={(data?.workflowInbox?.highPriorityCount ?? 0) > 0 ? "warning" : "default"}
                />
                <SummaryMetric
                    label="Unreconciled Accounts"
                    value={`${data?.period?.unreconciledAccountCount ?? 0}`}
                    detail={data?.period?.closeReady ? "Period close is clear on reconciliations." : "Close is still waiting on account work."}
                    tone={data?.period?.closeReady ? "success" : "warning"}
                />
            </div>

            {lowDataWorkspace ? (
                <SectionBand
                    eyebrow="Getting started"
                    title="This workspace is ready for its first operating cycle"
                    description="A quiet workspace is normal at the beginning. The fastest path is to bring in transactions, clear any review work, and then close out reconciliations."
                >
                    <div className="grid gap-4 lg:grid-cols-[1.2fr_0.8fr]">
                        <NextStepsList
                            title="Suggested first run"
                            items={[
                                "Import a recent bank or card statement so the dashboard can start tracking transaction volume and category trends.",
                                "Resolve any review-queue items created by ambiguous merchants before you move into reconciliation.",
                                "Start reconciliation once balances are available so period close stays grounded in the real statement data.",
                            ]}
                        />
                        <StatusBanner
                            tone="muted"
                            title="What will appear next"
                            message="As activity lands, this dashboard will light up with category movement, workflow pressure, and close-readiness signals."
                        />
                    </div>
                </SectionBand>
            ) : null}

            {data?.primaryAction ? (
                <SectionBand
                    eyebrow="Recommended next step"
                    title={data.primaryAction.label}
                    description={data.primaryAction.reason}
                    actions={
                        <Link
                            href={actionHref ?? "/dashboard"}
                            className="inline-flex rounded-md bg-amber-300 px-4 py-2.5 text-sm font-semibold text-black hover:bg-amber-200"
                        >
                            Open action
                        </Link>
                    }
                >
                    <div className="flex flex-wrap gap-3 text-sm">
                        <span className="rounded-full border border-amber-400/30 bg-amber-300/10 px-3 py-1 text-amber-100">
                            Urgency: {data.primaryAction.urgency}
                        </span>
                        <span className="rounded-full border border-white/10 bg-white/[0.04] px-3 py-1 text-zinc-300">
                            {data.primaryAction.itemCount} items connected
                        </span>
                    </div>
                </SectionBand>
            ) : null}

            <SectionBand
                eyebrow="Operational pulse"
                title="How the workspace is trending"
                description="Use these lightweight signals to decide whether to focus on queue cleanup, reconciliation, or simply keeping imports moving."
            >
                <div className="grid gap-5 lg:grid-cols-[1.15fr_0.85fr]">
                    <div className="space-y-4 rounded-lg border border-white/10 bg-white/[0.03] p-4">
                        <ProgressMeter
                            label="Close readiness"
                            value={closeCompletionValue}
                            total={closeCompletionDenominator}
                            tone={data?.period?.closeReady ? "success" : "warning"}
                        />
                        <ProgressMeter
                            label="High-priority workflow pressure"
                            value={workflowCounts.highPriorityCount}
                            total={Math.max(1, workflowCounts.openCount)}
                            tone={workflowCounts.highPriorityCount > 0 ? "warning" : "success"}
                        />
                        <ProgressMeter
                            label="Assigned vs. unassigned work"
                            value={workflowCounts.assignedToCurrentUserCount}
                            total={Math.max(1, workflowCounts.assignedToCurrentUserCount + workflowCounts.unassignedCount)}
                            tone={workflowCounts.unassignedCount > 0 ? "warning" : "success"}
                        />
                    </div>

                    <div className="grid gap-3">
                        <div className="rounded-lg border border-white/10 bg-white/[0.03] p-4">
                            <p className="text-xs uppercase tracking-[0.18em] text-zinc-500">
                                Workflow lane
                            </p>
                            <p className="mt-2 text-lg font-semibold text-white">
                                {workflowCounts.openCount > 0 ? "Review Queue" : "Clean"}
                            </p>
                            <p className="mt-2 text-sm text-zinc-400">
                                {workflowCounts.openCount > 0
                                    ? `${workflowCounts.openCount} task(s) are open, with ${workflowCounts.overdueCount} overdue and ${workflowCounts.dueTodayCount} due today.`
                                    : "No open review or workflow pressure is currently visible."}
                            </p>
                        </div>
                        <div className="rounded-lg border border-white/10 bg-white/[0.03] p-4">
                            <p className="text-xs uppercase tracking-[0.18em] text-zinc-500">
                                Close lane
                            </p>
                            <p className="mt-2 text-lg font-semibold text-white">
                                {data?.period?.closeReady ? "Ready to close" : "Needs reconciliation work"}
                            </p>
                            <p className="mt-2 text-sm text-zinc-400">
                                {data?.period?.closeReady
                                    ? "Reconciliations are no longer blocking this period."
                                    : `${data?.period?.unreconciledAccountCount ?? 0} account(s) still need attention before close can move forward.`}
                            </p>
                        </div>
                        <div className="rounded-lg border border-white/10 bg-white/[0.03] p-4">
                            <p className="text-xs uppercase tracking-[0.18em] text-zinc-500">
                                Attention score
                            </p>
                            <p className="mt-2 text-lg font-semibold text-white">
                                {reviewTaskPressure === 0 ? "Low" : reviewTaskPressure <= 2 ? "Moderate" : "High"}
                            </p>
                            <p className="mt-2 text-sm text-zinc-400">
                                This combines workflow pressure and unreconciled accounts into a simple operating signal.
                            </p>
                        </div>
                    </div>
                </div>
            </SectionBand>

            {expenseCategories.length > 0 ? (
                <SectionBand
                    eyebrow="Expense categories"
                    title="Where spend is moving"
                    description="Use the biggest category shifts to spot anomalies before they turn into cleanup work."
                >
                    <div className="mt-4 grid gap-3 md:grid-cols-2">
                        {expenseCategories.map((item) => (
                            <div
                                key={item.itemId}
                                className="rounded-lg border border-white/10 bg-white/[0.03] p-4"
                            >
                                <p className="text-sm text-zinc-400">{item.category}</p>

                                <p className="mt-1 text-lg font-semibold text-white">
                                    ${Number(item.amount ?? 0).toFixed(2)}
                                </p>

                                <p className="mt-1 text-xs text-zinc-500">
                                    {item.actionReason}
                                </p>
                            </div>
                        ))}
                    </div>
                </SectionBand>
            ) : (
                <SectionBand
                    eyebrow="Expense categories"
                    title="Where spend is moving"
                    description="Spending trends will appear here once categories have enough posted activity."
                >
                    <StatusBanner
                        tone="muted"
                        title="No category trend yet"
                        message="Import a few more posted transactions to light up category movement and month-over-month changes."
                    />
                </SectionBand>
            )}
        </main>
    );
}
