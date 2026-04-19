"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import {
    getReconciliationDashboard,
    startReconciliation,
    ReconciliationDashboard,
} from "@/lib/api/reconciliation";
import { getOrganizationId } from "@/lib/auth/session";

export default function ReconciliationPage() {
    const [organizationId] = useState(getOrganizationId);
    const [data, setData] = useState<ReconciliationDashboard | null>(null);
    const [loading, setLoading] = useState(() => Boolean(organizationId));
    const [error, setError] = useState("");
    const [openingBalance, setOpeningBalance] = useState("");
    const [statementEndingBalance, setStatementEndingBalance] = useState("");
    const [startingReconciliation, setStartingReconciliation] = useState(false);

    useEffect(() => {
        if (!organizationId) return;

        getReconciliationDashboard(organizationId)
            .then((result) => setData(result))
            .catch((err: Error) => setError(err.message))
            .finally(() => setLoading(false));
    }, [organizationId]);

    async function handleStartReconciliation(accountId: string, actionPath: string) {
        if (!organizationId || !data?.focusMonth) {
            setError("No organization ID found. Please sign in again.");
            return;
        }

        const parsedOpeningBalance = Number(openingBalance);
        const parsedStatementEndingBalance = Number(statementEndingBalance);

        if (!Number.isFinite(parsedOpeningBalance) || !Number.isFinite(parsedStatementEndingBalance)) {
            setError("Enter valid opening and statement ending balances before starting.");
            return;
        }

        setError("");
        setStartingReconciliation(true);

        try {
            await startReconciliation(organizationId, {
                financialAccountId: accountId,
                month: data.focusMonth,
                openingBalance: parsedOpeningBalance,
                statementEndingBalance: parsedStatementEndingBalance,
            });
            window.location.href = actionPath;
        } catch (err) {
            const message =
                err instanceof Error ? err.message : "Unable to start reconciliation.";
            setError(message);
        } finally {
            setStartingReconciliation(false);
        }
    }

    if (loading) {
        return <main className="p-6">Loading reconciliation workspace...</main>;
    }

    if (!organizationId || error) {
        return (
            <main className="p-6">
                <div className="rounded-md border border-red-200 bg-red-50 p-4 text-red-700">
                    {error || "No organization ID found. Please sign in again."}
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
                    <div className="mt-4 grid gap-3 md:max-w-xl md:grid-cols-2">
                        <label className="space-y-2 text-sm text-zinc-300">
                            <span>Opening Balance</span>
                            <input
                                className="w-full rounded-md border border-zinc-700 bg-black px-3 py-2 text-white outline-none"
                                inputMode="decimal"
                                type="number"
                                step="0.01"
                                value={openingBalance}
                                onChange={(event) => setOpeningBalance(event.target.value)}
                            />
                        </label>

                        <label className="space-y-2 text-sm text-zinc-300">
                            <span>Statement Ending Balance</span>
                            <input
                                className="w-full rounded-md border border-zinc-700 bg-black px-3 py-2 text-white outline-none"
                                inputMode="decimal"
                                type="number"
                                step="0.01"
                                value={statementEndingBalance}
                                onChange={(event) => setStatementEndingBalance(event.target.value)}
                            />
                        </label>

                        <button
                            onClick={() =>
                                handleStartReconciliation(
                                    unreconciledAccounts[0].accountId,
                                    unreconciledAccounts[0].actionPath
                                )
                            }
                            disabled={startingReconciliation}
                            className="inline-block rounded-md bg-red-600 px-5 py-2.5 text-sm font-semibold text-white hover:bg-red-700 transition disabled:opacity-50 md:col-span-2"
                        >
                            {startingReconciliation ? "Starting..." : "Start Reconciliation"}
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
