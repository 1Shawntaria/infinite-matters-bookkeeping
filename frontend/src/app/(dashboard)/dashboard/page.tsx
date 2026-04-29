"use client";

import { useQuery } from "@tanstack/react-query";
import { AuthActivityItem, listAuthActivity } from "@/lib/api/auth";
import {
    FinancialAccount,
    listFinancialAccounts,
} from "@/lib/api/accounts";
import {
    ImportedTransactionHistoryItem,
    listImportHistory,
} from "@/lib/api/imports";
import { useState } from "react";
import {
    getDashboardSnapshot,
    DashboardSnapshot,
} from "@/lib/api/dashboard";
import Link from "next/link";
import { mapBackendActionPathToFrontend } from "@/lib/navigation";
import { useOrganizationSession } from "@/lib/auth/session";
import { buildFocusMonthFollowUp, FollowUpAction } from "@/lib/close-follow-up";
import {
    LoadingPanel,
    MiniBarChart,
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
    const authActivityQuery = useQuery<AuthActivityItem[], Error>({
        queryKey: ["authActivity"],
        enabled: hydrated,
        queryFn: () => listAuthActivity(),
    });
    const accountsQuery = useQuery<FinancialAccount[], Error>({
        queryKey: ["financialAccounts", organizationId],
        enabled: hydrated && Boolean(organizationId),
        queryFn: () => listFinancialAccounts(organizationId),
    });
    const importHistoryQuery = useQuery<ImportedTransactionHistoryItem[], Error>({
        queryKey: ["importHistory", organizationId],
        enabled: hydrated && Boolean(organizationId),
        queryFn: () => listImportHistory(organizationId),
    });
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
    const accounts = accountsQuery.data ?? [];
    const importHistory = importHistoryQuery.data ?? [];
    const loading =
        hydrated && organizationId
            ? dashboardQuery.isLoading || accountsQuery.isLoading || importHistoryQuery.isLoading
            : false;
    const queryError =
        dashboardQuery.error?.message ??
        accountsQuery.error?.message ??
        importHistoryQuery.error?.message ??
        authActivityQuery.error?.message ??
        "";
    const actionHref = data?.primaryAction
        ? mapBackendActionPathToFrontend(data.primaryAction.actionPath)
        : null;
    const expenseCategories = Array.isArray(data?.expenseCategories)
        ? data.expenseCategories
        : [];
    const staleAccounts = Array.isArray(data?.staleAccounts)
        ? data.staleAccounts
        : [];
    const recentNotifications = Array.isArray(data?.recentNotifications)
        ? data.recentNotifications
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
    const topExpenseCategories = [...expenseCategories]
        .sort((left, right) => right.amount - left.amount)
        .slice(0, 4);
    const notificationItems = recentNotifications.slice(0, 3);
    const staleAccountItems = staleAccounts.slice(0, 3);
    const activeAccountCount = staleAccounts.length + (data?.period?.unreconciledAccountCount ?? 0);
    const setupSteps = [
        {
            label: "Create an account",
            complete: activeAccountCount > 0,
            detail: activeAccountCount > 0
                ? "At least one financial account is ready for activity."
                : "Create the first account in Setup & Import.",
        },
        {
            label: "Import transactions",
            complete: (data?.postedTransactionCount ?? 0) > 0,
            detail: (data?.postedTransactionCount ?? 0) > 0
                ? `${data?.postedTransactionCount ?? 0} posted transaction(s) are already in the books.`
                : "Bring in a CSV statement to populate the workspace.",
        },
        {
            label: "Clear workflow decisions",
            complete: workflowCounts.openCount === 0,
            detail: workflowCounts.openCount === 0
                ? "No open categorization tasks are blocking progress."
                : `${workflowCounts.openCount} queue item(s) still need review.`,
        },
    ];
    const completedSetupSteps = setupSteps.filter((step) => step.complete).length;
    const latestImport = importHistory[0] ?? null;
    const importsByAccount = accounts.map((account) => {
        const accountImports = importHistory.filter(
            (item) => item.financialAccountId === account.id
        );

        return {
            account,
            importCount: accountImports.length,
            latestImport: accountImports[0] ?? null,
        };
    });
    const activeImportAccounts = importsByAccount
        .filter((item) => item.importCount > 0)
        .slice(0, 3);
    const activityTimeline = [
        ...importHistory.slice(0, 4).map((item) => ({
            id: `import-${item.transactionId}`,
            time: item.importedAt,
            title: `${item.financialAccountName}: ${item.merchant}`,
            detail: `${item.status.replaceAll("_", " ")} through ${item.route.toLowerCase()}`,
            lane: "Import",
        })),
        ...(authActivityQuery.data ?? []).slice(0, 4).map((item) => ({
            id: `auth-${item.id}`,
            time: item.createdAt,
            title: item.eventType.replaceAll("_", " "),
            detail: item.details,
            lane: "Security",
        })),
        ...notificationItems.slice(0, 3).map((item) => ({
            id: `notification-${item.id}`,
            time: item.createdAt,
            title: item.category,
            detail: item.message,
            lane: "Notification",
        })),
    ]
        .sort((left, right) => new Date(right.time).getTime() - new Date(left.time).getTime())
        .slice(0, 6);
    const closeControlFollowUp: FollowUpAction | null = buildFocusMonthFollowUp({
        focusMonth: data?.focusMonth ?? "",
        attestationRoutingGap: 0,
        attestationGap: 0,
        signoffGap: 0,
        ownerSignoffGap: 0,
        pendingPlaybookCount: 0,
        requireOwnerSignoffBeforeClose: false,
        context: "dashboard",
        closeReady: data?.period?.closeReady ?? false,
        unreconciledAccountCount: data?.period?.unreconciledAccountCount ?? 0,
    });

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

            <SectionBand
                eyebrow="Import activity"
                title="What the latest imports changed"
                description="This keeps recent transaction ingestion visible from the dashboard so the team can see which account moved, what landed, and where the next follow-up is likely to show up."
                actions={
                    <Link
                        href="/setup"
                        className="inline-flex rounded-md border border-white/10 px-4 py-2.5 text-sm text-zinc-100 hover:bg-white/[0.05]"
                    >
                        Open setup & import
                    </Link>
                }
            >
                <div className="grid gap-6 xl:grid-cols-[0.8fr_1.2fr]">
                    <div className="grid gap-4 sm:grid-cols-2">
                        <SummaryMetric
                            label="Tracked accounts"
                            value={`${accounts.length}`}
                            detail="Financial accounts available for import and reconciliation."
                        />
                        <SummaryMetric
                            label="Latest import"
                            value={
                                latestImport
                                    ? new Date(latestImport.importedAt).toLocaleDateString("en-US", {
                                          month: "short",
                                          day: "numeric",
                                      })
                                    : "Not started"
                            }
                            detail={
                                latestImport
                                    ? `${latestImport.financialAccountName} · ${latestImport.merchant}`
                                    : "The setup flow will start surfacing imports here once the first CSV lands."
                            }
                            tone={latestImport ? "success" : "default"}
                        />
                    </div>

                    <div className="rounded-lg border border-white/10 bg-white/[0.03] p-4">
                        <div className="flex items-center justify-between gap-3">
                            <h3 className="text-sm font-semibold text-white">Account import summaries</h3>
                            <span className="text-xs text-zinc-500">
                                {activeImportAccounts.length} active
                            </span>
                        </div>
                        {activeImportAccounts.length > 0 ? (
                            <div className="mt-4 space-y-3">
                                {activeImportAccounts.map(({ account, importCount, latestImport: accountLatestImport }) => (
                                    <div
                                        key={account.id}
                                        className="rounded-md border border-white/10 bg-black/20 px-3 py-3"
                                    >
                                        <div className="flex items-center justify-between gap-3">
                                            <div>
                                                <p className="text-sm font-medium text-white">
                                                    {account.name}
                                                </p>
                                                <p className="mt-1 text-xs text-zinc-400">
                                                    {account.accountType} · {account.currency} · {importCount} imported row(s)
                                                </p>
                                            </div>
                                            <span className="text-xs text-zinc-500">
                                                {accountLatestImport
                                                    ? new Date(accountLatestImport.importedAt).toLocaleDateString("en-US", {
                                                          month: "short",
                                                          day: "numeric",
                                                      })
                                                    : "No imports"}
                                            </span>
                                        </div>
                                        <p className="mt-2 text-xs text-zinc-400">
                                            {accountLatestImport
                                                ? `${accountLatestImport.merchant} landed ${accountLatestImport.status.toLowerCase().replaceAll("_", " ")} through ${accountLatestImport.route.toLowerCase()}.`
                                                : "No import activity yet."}
                                        </p>
                                    </div>
                                ))}
                            </div>
                        ) : (
                            <StatusBanner
                                tone="muted"
                                title="No import activity yet"
                                message="Run the setup flow to create an account and import the first statement. This dashboard section will then show the freshest account movement."
                            />
                        )}
                    </div>
                </div>
            </SectionBand>

            {lowDataWorkspace ? (
                <SectionBand
                    eyebrow="Getting started"
                    title="This workspace is ready for its first operating cycle"
                    description="A quiet workspace is normal at the beginning. We can guide the first run from here instead of making you hunt for where setup starts."
                    actions={
                        <div className="flex flex-wrap gap-3">
                            <Link
                                href="/setup?welcome=1"
                                className="inline-flex rounded-md bg-emerald-300 px-4 py-2.5 text-sm font-semibold text-black hover:bg-emerald-200"
                            >
                                Use your own CSV
                            </Link>
                            <Link
                                href="/setup?demo=1&welcome=1"
                                className="inline-flex rounded-md border border-white/10 px-4 py-2.5 text-sm text-zinc-100 hover:bg-white/[0.05]"
                            >
                                Load sample workspace
                            </Link>
                        </div>
                    }
                >
                    <div className="grid gap-4 lg:grid-cols-[1.1fr_0.9fr]">
                        <div className="rounded-lg border border-white/10 bg-white/[0.03] p-4">
                            <p className="text-xs font-medium uppercase tracking-[0.18em] text-zinc-500">
                                Guided first run
                            </p>
                            <h3 className="mt-2 text-lg font-semibold text-white">
                                Choose the onboarding lane that fits this moment
                            </h3>
                            <div className="mt-4 space-y-3 text-sm text-zinc-400">
                                <p>
                                    <span className="font-medium text-zinc-200">Sample workspace:</span>{" "}
                                    instantly populate the product with realistic data for a guided tour.
                                </p>
                                <p>
                                    <span className="font-medium text-zinc-200">Real CSV import:</span>{" "}
                                    validate your actual statement flow and let the workspace build from live activity.
                                </p>
                            </div>
                        </div>
                        <NextStepsList
                            title="What happens after setup"
                            items={[
                                "The dashboard starts showing spend, workflow pressure, and close-readiness signals.",
                                "Ambiguous merchants route into the review queue with suggested categories.",
                                "Account activity becomes available for reconciliation and close preparation.",
                            ]}
                        />
                    </div>
                </SectionBand>
            ) : null}

            {completedSetupSteps < setupSteps.length ? (
                <SectionBand
                    eyebrow="Setup progress"
                    title="You are still in the first-run setup loop"
                    description="This checklist keeps the team oriented on what still needs to happen before the workspace feels fully operational."
                    actions={
                        <Link
                            href="/setup"
                            className="inline-flex rounded-md border border-white/10 px-4 py-2.5 text-sm text-zinc-100 hover:bg-white/[0.05]"
                        >
                            Continue setup
                        </Link>
                    }
                >
                    <div className="grid gap-4 lg:grid-cols-[0.8fr_1.2fr]">
                        <SummaryMetric
                            label="Steps completed"
                            value={`${completedSetupSteps}/${setupSteps.length}`}
                            detail="Finish these once and the dashboard becomes much more self-explanatory."
                            tone={completedSetupSteps === setupSteps.length ? "success" : "warning"}
                        />
                        <div className="grid gap-3">
                            {setupSteps.map((step) => (
                                <div
                                    key={step.label}
                                    className="rounded-lg border border-white/10 bg-white/[0.03] px-4 py-4"
                                >
                                    <div className="flex items-center justify-between gap-3">
                                        <p className="text-sm font-semibold text-white">
                                            {step.label}
                                        </p>
                                        <span
                                            className={[
                                                "rounded-full px-2.5 py-1 text-xs",
                                                step.complete
                                                    ? "border border-emerald-400/30 bg-emerald-300/10 text-emerald-100"
                                                    : "border border-amber-400/30 bg-amber-300/10 text-amber-100",
                                            ].join(" ")}
                                        >
                                            {step.complete ? "Done" : "Next"}
                                        </span>
                                    </div>
                                    <p className="mt-2 text-sm text-zinc-400">{step.detail}</p>
                                </div>
                            ))}
                        </div>
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

            {closeControlFollowUp ? (
                <SectionBand
                    eyebrow="Close follow-up"
                    title={closeControlFollowUp.title}
                    description="Use this shortcut when the most important next move is tied to the current focus month."
                >
                    <div className="rounded-lg border border-emerald-400/20 bg-emerald-300/10 p-5">
                        <p className="text-sm leading-6 text-zinc-100">{closeControlFollowUp.message}</p>
                        <div className="mt-4 flex flex-wrap gap-3">
                            <Link
                                href={closeControlFollowUp.primaryHref}
                                className="rounded-md bg-emerald-300 px-4 py-2.5 text-sm font-semibold text-black hover:bg-emerald-200"
                            >
                                {closeControlFollowUp.primaryLabel}
                            </Link>
                            <Link
                                href={closeControlFollowUp.secondaryHref}
                                className="rounded-md border border-white/10 px-4 py-2.5 text-sm text-zinc-100 hover:bg-white/[0.05]"
                            >
                                {closeControlFollowUp.secondaryLabel}
                            </Link>
                        </div>
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

            <div className="grid gap-6 xl:grid-cols-[1.15fr_0.85fr]">
                <SectionBand
                    eyebrow="Volume and mix"
                    title="Where activity is showing up"
                    description="These quick visuals help you see whether work is clustering around categorization, close, or raw transaction volume."
                >
                    <div className="grid gap-4 lg:grid-cols-2">
                        <MiniBarChart
                            title="Workflow load"
                            items={[
                                {
                                    label: "Open tasks",
                                    value: workflowCounts.openCount,
                                    helper: `${workflowCounts.overdueCount} overdue, ${workflowCounts.dueTodayCount} due today`,
                                },
                                {
                                    label: "High priority",
                                    value: workflowCounts.highPriorityCount,
                                    helper: "Tasks that should move first",
                                },
                                {
                                    label: "Unassigned",
                                    value: workflowCounts.unassignedCount,
                                    helper: "Items still waiting for ownership",
                                },
                            ]}
                        />
                        <MiniBarChart
                            title="Expense concentration"
                            items={
                                topExpenseCategories.length > 0
                                    ? topExpenseCategories.map((item) => ({
                                          label: item.category,
                                          value: item.amount,
                                          helper: item.actionReason,
                                      }))
                                    : [
                                          {
                                              label: "No spend trend yet",
                                              value: 1,
                                              helper: "Import and post more activity to start comparing categories.",
                                          },
                                      ]
                            }
                        />
                    </div>
                </SectionBand>

                <SectionBand
                    eyebrow="Recent activity"
                    title="What just happened in this workspace"
                    description="This timeline keeps imports, security events, and operational notifications visible together."
                    actions={
                        <Link
                            href="/activity"
                            className="inline-flex rounded-md border border-white/10 px-4 py-2.5 text-sm text-zinc-100 hover:bg-white/[0.05]"
                        >
                            Open full activity
                        </Link>
                    }
                >
                    <div className="grid gap-4">
                        <div className="rounded-lg border border-white/10 bg-white/[0.03] p-4">
                            <div className="flex items-center justify-between gap-3">
                                <h3 className="text-sm font-semibold text-white">Activity timeline</h3>
                                <span className="text-xs text-zinc-500">
                                    {activityTimeline.length} recent
                                </span>
                            </div>
                            {activityTimeline.length > 0 ? (
                                <div className="mt-4 space-y-3">
                                    {activityTimeline.map((item) => (
                                        <div
                                            key={item.id}
                                            className="rounded-md border border-white/10 bg-black/20 px-3 py-3"
                                        >
                                            <div className="flex items-center justify-between gap-3">
                                                <div>
                                                    <p className="text-sm font-medium text-white">
                                                        {item.title}
                                                    </p>
                                                    <p className="mt-1 text-xs text-zinc-400">
                                                        {item.detail}
                                                    </p>
                                                </div>
                                                <div className="text-right">
                                                    <p className="text-xs text-zinc-500">{item.lane}</p>
                                                    <p className="mt-1 text-xs text-zinc-400">
                                                        {new Date(item.time).toLocaleString("en-US", {
                                                            month: "short",
                                                            day: "numeric",
                                                            hour: "numeric",
                                                            minute: "2-digit",
                                                        })}
                                                    </p>
                                                </div>
                                            </div>
                                        </div>
                                    ))}
                                </div>
                            ) : (
                                <p className="mt-3 text-sm text-zinc-400">
                                    Recent imports, auth events, and notifications will collect here as the workspace gets used.
                                </p>
                            )}
                        </div>

                        <div className="rounded-lg border border-white/10 bg-white/[0.03] p-4">
                            <div className="flex items-center justify-between gap-3">
                                <h3 className="text-sm font-semibold text-white">Stale accounts</h3>
                                <span className="text-xs text-zinc-500">
                                    {staleAccounts.length} tracked
                                </span>
                            </div>
                            {staleAccountItems.length > 0 ? (
                                <div className="mt-4 space-y-3">
                                    {staleAccountItems.map((account) => (
                                        <div
                                            key={account.itemId}
                                            className="rounded-md border border-white/10 bg-black/20 px-3 py-3"
                                        >
                                            <div className="flex items-center justify-between gap-3">
                                                <p className="text-sm font-medium text-white">
                                                    {account.accountName}
                                                </p>
                                                <span className="text-xs text-zinc-500">
                                                    {account.daysSinceActivity}d quiet
                                                </span>
                                            </div>
                                            <p className="mt-1 text-xs text-zinc-400">
                                                {account.actionReason}
                                            </p>
                                        </div>
                                    ))}
                                </div>
                            ) : (
                                <p className="mt-3 text-sm text-zinc-400">
                                    No stale-account pressure is visible right now.
                                </p>
                            )}
                        </div>

                        <div className="rounded-lg border border-white/10 bg-white/[0.03] p-4">
                            <div className="flex items-center justify-between gap-3">
                                <h3 className="text-sm font-semibold text-white">Recent notifications</h3>
                                <span className="text-xs text-zinc-500">
                                    {recentNotifications.length} recent
                                </span>
                            </div>
                            {notificationItems.length > 0 ? (
                                <div className="mt-4 space-y-3">
                                    {notificationItems.map((notification) => (
                                        <div
                                            key={notification.id}
                                            className="rounded-md border border-white/10 bg-black/20 px-3 py-3"
                                        >
                                            <div className="flex items-center justify-between gap-3">
                                                <p className="text-sm font-medium text-white">
                                                    {notification.category}
                                                </p>
                                                <span className="text-xs text-zinc-500">
                                                    {notification.deliveryState}
                                                </span>
                                            </div>
                                            <p className="mt-1 text-xs text-zinc-400">
                                                {notification.message}
                                            </p>
                                        </div>
                                    ))}
                                </div>
                            ) : (
                                <p className="mt-3 text-sm text-zinc-400">
                                    No recent notification activity needs attention.
                                </p>
                            )}
                        </div>
                    </div>
                </SectionBand>
            </div>

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
