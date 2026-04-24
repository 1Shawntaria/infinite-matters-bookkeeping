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

    if (!hydrated || loading) {
        return <main className="p-6">Loading dashboard...</main>;
    }

    if (!organizationId || error || queryError) {
        return (
            <main className="p-6">
                <div className="rounded-md border border-red-200 bg-red-50 p-4 text-red-700">
                    {error || queryError || "No organization ID found. Please sign in again."}
                </div>
            </main>
        );
    }

    return (
        <main className="space-y-8 p-4 sm:p-6 lg:p-8">
            <div className="rounded-xl border border-zinc-900/80 bg-black/45 p-6 backdrop-blur">
                <div className="flex flex-col gap-6 lg:flex-row lg:items-end lg:justify-between">
                    <div className="space-y-3">
                        <p className="text-xs uppercase tracking-[0.22em] text-emerald-300">
                            Workspace snapshot
                        </p>
                        <div>
                            <h1 className="text-3xl font-semibold text-white">Dashboard</h1>
                            <p className="mt-2 max-w-2xl text-sm text-zinc-400">
                                Monitor cash position, review pressure, and close readiness without
                                hunting through separate screens.
                            </p>
                        </div>
                    </div>

                    <div className="grid gap-3 sm:grid-cols-2">
                        <div className="rounded-lg border border-zinc-800 bg-zinc-950/70 px-4 py-3">
                            <p className="text-xs uppercase tracking-[0.18em] text-zinc-500">
                                Focus Month
                            </p>
                            <p className="mt-2 text-lg font-semibold text-white">
                                {data?.focusMonth ?? "-"}
                            </p>
                        </div>
                        <div className="rounded-lg border border-zinc-800 bg-zinc-950/70 px-4 py-3">
                            <p className="text-xs uppercase tracking-[0.18em] text-zinc-500">
                                Recommended next
                            </p>
                            <p className="mt-2 text-sm font-medium text-white">
                                {data?.primaryAction?.label ?? "No urgent action right now"}
                            </p>
                        </div>
                    </div>
                </div>
            </div>

            <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
                <div className="rounded-xl border border-zinc-900/80 bg-black/45 p-5 backdrop-blur">
                    <p className="text-sm text-zinc-400">Cash Balance</p>
                    <p className="mt-2 text-2xl font-semibold text-white">
                        ${Number(data?.cashBalance ?? 0).toFixed(2)}
                    </p>
                </div>

                <div className="rounded-xl border border-zinc-900/80 bg-black/45 p-5 backdrop-blur">
                    <p className="text-sm text-zinc-400">Posted Transactions</p>
                    <p className="mt-2 text-2xl font-semibold text-white">
                        {data?.postedTransactionCount ?? 0}
                    </p>
                </div>

                <div className="rounded-xl border border-zinc-900/80 bg-black/45 p-5 backdrop-blur">
                    <p className="text-sm text-zinc-400">Open Tasks</p>
                    <p className="mt-2 text-2xl font-semibold text-white">
                        {data?.workflowInbox?.openCount ?? 0}
                    </p>
                </div>

                <div className="rounded-xl border border-zinc-900/80 bg-black/45 p-5 backdrop-blur">
                    <p className="text-sm text-zinc-400">Unreconciled Accounts</p>
                    <p className="mt-2 text-2xl font-semibold text-white">
                        {data?.period?.unreconciledAccountCount ?? 0}
                    </p>
                </div>
            </div>

            {data?.primaryAction ? (
                <div className="rounded-xl border border-amber-400/40 bg-amber-300/10 p-6">
                    <p className="text-sm text-zinc-400">Recommended Next Step</p>
                    <h2 className="mt-2 text-xl font-semibold text-white">
                        {data.primaryAction.label}
                    </h2>
                    <p className="mt-2 text-sm text-zinc-300">
                        {data.primaryAction.reason}
                    </p>
                    <p className="mt-3 text-xs uppercase tracking-wide text-zinc-500">
                        Urgency: {data.primaryAction.urgency}
                    </p>

                    <Link
                        href={mapBackendActionPathToFrontend(data.primaryAction.actionPath)}
                        className="mt-4 inline-block rounded-md bg-amber-300 px-5 py-2.5 text-sm font-semibold text-black transition hover:bg-amber-200"
                    >
                        {data.primaryAction.label}
                    </Link>
                </div>
            ) : null}

            {Array.isArray(data?.expenseCategories) &&
            data.expenseCategories.length > 0 ? (
                <div className="rounded-xl border border-zinc-900/80 bg-black/45 p-5 backdrop-blur">
                    <div className="space-y-1">
                        <p className="text-sm text-zinc-400">Expense Categories</p>
                        <h2 className="text-xl font-semibold text-white">
                            Where spend is moving
                        </h2>
                    </div>

                    <div className="mt-4 grid gap-3 md:grid-cols-2">
                        {data.expenseCategories.map((item) => (
                            <div
                                key={item.itemId}
                                className="rounded-lg border border-zinc-800 bg-zinc-950/80 p-4"
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
                </div>
            ) : null}

        </main>
    );
}
