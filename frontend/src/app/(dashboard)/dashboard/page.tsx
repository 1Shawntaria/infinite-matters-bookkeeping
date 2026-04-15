"use client";

import { useEffect, useState } from "react";
import {
    getDashboardSnapshot,
    DashboardSnapshot,
} from "@/lib/api/dashboard";

import Link from "next/link";
import { mapBackendActionPathToFrontend } from "@/lib/navigation";

export default function DashboardPage() {
    const [data, setData] = useState<DashboardSnapshot | null>(null);
    const [error, setError] = useState("");
    const [loading, setLoading] = useState(true);

    useEffect(() => {
        const organizationId = localStorage.getItem("organizationId");

        if (!organizationId) {
            setError("No organization ID found. Please sign in again.");
            setLoading(false);
            return;
        }

        getDashboardSnapshot(organizationId)
            .then((result) => setData(result))
            .catch((err: Error) => setError(err.message))
            .finally(() => setLoading(false));
    }, []);

    if (loading) {
        return <main className="p-6">Loading dashboard...</main>;
    }

    if (error) {
        return (
            <main className="p-6">
                <div className="rounded-md border border-red-200 bg-red-50 p-4 text-red-700">
                    {error}
                </div>
            </main>
        );
    }

    return (
        <main className="space-y-6 p-6">
            {/* Header */}
            <div className="space-y-4">
                <div>
                    <h1 className="text-2xl font-semibold text-white">Dashboard</h1>
                    <p className="text-sm text-zinc-400">
                        Snapshot of your bookkeeping workspace.
                    </p>
                </div>
            </div>

            {/* KPI Cards */}
            <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
                <div className="rounded-xl border border-zinc-800 bg-zinc-900 p-4">
                    <p className="text-sm text-zinc-400">Cash Balance</p>
                    <p className="mt-2 text-2xl font-semibold text-white">
                        ${Number(data?.cashBalance ?? 0).toFixed(2)}
                    </p>
                </div>

                <div className="rounded-xl border border-zinc-800 bg-zinc-900 p-4">
                    <p className="text-sm text-zinc-400">Posted Transactions</p>
                    <p className="mt-2 text-2xl font-semibold text-white">
                        {data?.postedTransactionCount ?? 0}
                    </p>
                </div>

                <div className="rounded-xl border border-zinc-800 bg-zinc-900 p-4">
                    <p className="text-sm text-zinc-400">Open Tasks</p>
                    <p className="mt-2 text-2xl font-semibold text-white">
                        {data?.workflowInbox?.openCount ?? 0}
                    </p>
                </div>

                <div className="rounded-xl border border-zinc-800 bg-zinc-900 p-4">
                    <p className="text-sm text-zinc-400">Unreconciled Accounts</p>
                    <p className="mt-2 text-2xl font-semibold text-white">
                        {data?.period?.unreconciledAccountCount ?? 0}
                    </p>
                </div>
            </div>

            {/* Primary Action */}
            {data?.primaryAction ? (
                <div className="rounded-xl border border-red-500/50 bg-zinc-900 p-5">
                    <p className="text-sm text-zinc-400">Recommended Next Step</p>
                    <h2 className="mt-2 text-lg font-semibold text-white">
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
                        className="mt-4 inline-block rounded-md bg-red-600 px-5 py-2.5 text-sm font-semibold text-white hover:bg-red-700 transition"
                    >
                        {data.primaryAction.label}
                    </Link>
                </div>
            ) : null}

            {/* Expense Categories */}
            {Array.isArray(data?.expenseCategories) &&
            data.expenseCategories.length > 0 ? (
                <div className="rounded-xl border border-zinc-800 bg-zinc-900 p-4">
                    <p className="text-sm text-zinc-400">Expense Categories</p>

                    <div className="mt-4 grid gap-3 md:grid-cols-2">
                        {data.expenseCategories.map((item) => (
                            <div
                                key={item.itemId}
                                className="rounded-lg border border-zinc-800 bg-black p-3"
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

            {/* Raw Snapshot (Debug Only) */}
            <div className="rounded-xl border border-zinc-800 bg-zinc-900 p-4">
                <p className="text-sm text-zinc-400">System Details (Debug)</p>

                <pre className="mt-3 max-h-[500px] overflow-auto rounded-md bg-black p-4 text-xs leading-6 text-green-200">
          {JSON.stringify(data, null, 2)}
        </pre>
            </div>
        </main>
    );
}