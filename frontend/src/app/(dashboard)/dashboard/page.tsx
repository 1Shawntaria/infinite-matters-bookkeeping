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
    PageHero,
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
