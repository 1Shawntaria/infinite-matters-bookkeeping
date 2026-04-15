"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import {
    getReconciliationDashboard,
    startReconciliation,
    ReconciliationDashboard,
} from "@/lib/api/reconciliation";

export default function ReconciliationPage() {
    const [data, setData] = useState<ReconciliationDashboard | null>(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState("");

    useEffect(() => {
        const organizationId = localStorage.getItem("organizationId");

        if (!organizationId) {
            setError("No organization ID found. Please sign in again.");
            setLoading(false);
            return;
        }

        getReconciliationDashboard(organizationId)
            .then((result) => setData(result))
            .catch((err: Error) => setError(err.message))
            .finally(() => setLoading(false));
    }, []);

    if (loading) {
        return <main className="p-6">Loading reconciliation workspace...</main>;
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

    const unreconciledAccounts = data?.unreconciledAccounts ?? [];

    return (
        <main className="space-y-6 p-6">
            <div>
                <h1 className="text-2xl font-semibold text-white">Reconciliation</h1>
                <p className="text-sm text-zinc-400">
                    Manage period-close readiness and unresolved account reconciliation
                    work.
                </p>
            </div>

            <div className="grid gap-4 md:grid-cols-3">
                <div className="rounded-xl border border-zinc-800 bg-zinc-900 p-4">
                    <p className="text-sm text-zinc-400">Focus Month</p>
                    <p className="mt-2 text-2xl font-semibold text-white">
                        {data?.focusMonth ?? "-"}
                    </p>
                </div>

                <div className="rounded-xl border border-zinc-800 bg-zinc-900 p-4">
                    <p className="text-sm text-zinc-400">Unreconciled Accounts</p>
                    <p className="mt-2 text-2xl font-semibold text-white">
                        {data?.period?.unreconciledAccountCount ?? unreconciledAccounts.length}
                    </p>
                </div>

                <div className="rounded-xl border border-zinc-800 bg-zinc-900 p-4">
                    <p className="text-sm text-zinc-400">Close Ready</p>
                    <p className="mt-2 text-2xl font-semibold text-white">
                        {data?.period?.closeReady ? "Yes" : "No"}
                    </p>
                </div>
            </div>

            <div className="rounded-xl border border-zinc-800 bg-zinc-900 p-5">
                <p className="text-sm text-zinc-400">Period Close Status</p>

                <h2 className="mt-2 text-lg font-semibold text-white">
                    {data?.period?.closeReady
                        ? "This period is ready to close"
                        : "This period is not ready to close"}
                </h2>

                <p className="mt-2 text-sm text-zinc-300">
                    {data?.period?.recommendedActionLabel ??
                        "No recommended action is currently available."}
                </p>

                {data?.period?.recommendedActionUrgency ? (
                    <p className="mt-3 text-xs uppercase tracking-wide text-zinc-500">
                        Urgency: {data.period.recommendedActionUrgency}
                    </p>
                ) : null}

                {!data?.period?.closeReady && unreconciledAccounts.length > 0 && (
                    <div className="mt-4">
                        <button
                            onClick={async () => {
                                const organizationId = localStorage.getItem("organizationId");
                                if (!organizationId) return;

                                const account = unreconciledAccounts[0];

                                const result = await startReconciliation(organizationId, {
                                    financialAccountId: account.accountId,
                                    month: data?.focusMonth!,
                                    openingBalance: 0, // temporary
                                    statementEndingBalance: 0, // temporary
                                });
                                console.log("Reconciliation session:", result);

                                window.location.href = account.actionPath;
                            }}
                            className="inline-block rounded-md bg-red-600 px-5 py-2.5 text-sm font-semibold text-white hover:bg-red-700 transition"
                        >
                            Start Reconciliation
                        </button>
                    </div>
                )}
            </div>

            <div className="rounded-xl border border-zinc-800 bg-zinc-900 p-5">
                <div className="flex items-center justify-between">
                    <div>
                        <p className="text-sm text-zinc-400">Unreconciled Accounts</p>
                        <h2 className="mt-1 text-xl font-semibold text-white">
                            Accounts needing attention
                        </h2>
                    </div>
                </div>

                <div className="mt-4 space-y-4">
                    {unreconciledAccounts.length === 0 ? (
                        <p className="text-sm text-green-400">
                            No unreconciled accounts remaining.
                        </p>
                    ) : (
                        unreconciledAccounts.map((account) => (
                            <div
                                key={account.itemId}
                                className="rounded-lg border border-zinc-800 bg-black p-5 hover:border-zinc-700 transition"
                            >
                                <div className="flex flex-col gap-5 lg:flex-row lg:items-center lg:justify-between">
                                    <div className="space-y-2">
                                        <div className="flex flex-wrap items-center gap-2">
                                            <h3 className="text-lg font-semibold text-white">
                                                {account.accountName}
                                            </h3>

                                            <span className="rounded-full border border-red-500/30 bg-red-500/10 px-2 py-1 text-xs font-medium text-red-300">
                        Needs Reconciliation
                      </span>
                                        </div>

                                        <div className="grid gap-3 text-sm text-zinc-300 md:grid-cols-2 xl:grid-cols-4">
                                            <p>
                                                <span className="text-zinc-500">Name:</span>{" "}
                                                {account.accountName}
                                            </p>
                                            <p>
                                                <span className="text-zinc-500">Type:</span>{" "}
                                                {account.accountType}
                                            </p>
                                            <p>
                                                <span className="text-zinc-500">Last Activity:</span>{" "}
                                                {account.lastTransactionDate}
                                            </p>
                                            <p className="text-sm">
                                                <span className="text-zinc-500">Days Since Activity:</span>{" "}
                                                <span className="font-semibold text-red-400">
                          {account.daysSinceActivity}
                        </span>
                                            </p>
                                        </div>
                                    </div>

                                    <div className="flex min-w-[260px] flex-col gap-3">
                                        <Link
                                            href={account.actionPath}
                                            className="rounded-md bg-red-500 px-4 py-2 text-center text-sm font-medium text-white hover:bg-red-600 transition"
                                        >
                                            Review Account
                                        </Link>

                                        <button
                                            disabled
                                            className="cursor-not-allowed rounded-md border border-zinc-700 px-4 py-2 text-sm font-medium text-zinc-500 opacity-60"
                                        >
                                            Mark Reconciled
                                        </button>
                                    </div>
                                </div>
                            </div>
                        ))
                    )}
                </div>
            </div>

            <div className="rounded-xl border border-zinc-800 bg-zinc-900 p-5">
                <p className="text-sm text-zinc-400">Reconciliation Tasks</p>

                {unreconciledAccounts.length === 0 ? (
                    <p className="mt-2 text-sm text-green-400">
                        All accounts are reconciled for this period.
                    </p>
                ) : (
                    <p className="mt-2 text-sm text-zinc-300">
                        You have {unreconciledAccounts.length} account(s)
                        remaining to reconcile.
                    </p>
                )}
            </div>
        </main>
    );
}