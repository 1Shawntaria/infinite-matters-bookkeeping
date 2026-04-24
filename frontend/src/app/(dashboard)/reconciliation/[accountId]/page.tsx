"use client";

import { use, useEffect, useState } from "react";
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import {
    completeReconciliation,
    getReconciliationAccountDetail,
    ReconciliationAccountDetail,
    startReconciliation,
} from "@/lib/api/reconciliation";
import { getOrganizationId } from "@/lib/auth/session";

type AccountReconciliationPageProps = {
    params: Promise<{
        accountId: string;
    }>;
};

export default function AccountReconciliationPage({
    params,
}: AccountReconciliationPageProps) {
    const router = useRouter();
    const searchParams = useSearchParams();
    const { accountId } = use(params);
    const [organizationId] = useState(getOrganizationId);
    const [detail, setDetail] = useState<ReconciliationAccountDetail | null>(null);
    const [loading, setLoading] = useState(() => Boolean(organizationId));
    const [error, setError] = useState("");
    const [openingBalance, setOpeningBalance] = useState("");
    const [statementEndingBalance, setStatementEndingBalance] = useState("");
    const [starting, setStarting] = useState(false);
    const [completing, setCompleting] = useState(false);

    const focusMonth = searchParams.get("month") || undefined;

    useEffect(() => {
        if (!organizationId) return;

        getReconciliationAccountDetail(organizationId, accountId, focusMonth)
            .then((result) => {
                setDetail(result);
                if (result.session) {
                    setOpeningBalance(String(result.session.openingBalance));
                    setStatementEndingBalance(String(result.session.statementEndingBalance));
                }
            })
            .catch((err: Error) => setError(err.message))
            .finally(() => setLoading(false));
    }, [accountId, focusMonth, organizationId]);

    async function refreshDetail() {
        if (!organizationId) return;
        const result = await getReconciliationAccountDetail(organizationId, accountId, focusMonth);
        setDetail(result);
    }

    async function handleStart() {
        if (!organizationId || !detail) {
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
        setStarting(true);
        try {
            await startReconciliation(organizationId, {
                financialAccountId: detail.financialAccountId,
                month: detail.focusMonth,
                openingBalance: parsedOpeningBalance,
                statementEndingBalance: parsedStatementEndingBalance,
            });
            await refreshDetail();
        } catch (err) {
            setError(err instanceof Error ? err.message : "Unable to start reconciliation.");
        } finally {
            setStarting(false);
        }
    }

    async function handleComplete() {
        if (!organizationId || !detail?.session) {
            setError("No reconciliation session is available to complete.");
            return;
        }

        setError("");
        setCompleting(true);
        try {
            await completeReconciliation(organizationId, detail.session.id);
            await refreshDetail();
        } catch (err) {
            setError(err instanceof Error ? err.message : "Unable to complete reconciliation.");
        } finally {
            setCompleting(false);
        }
    }

    if (loading) {
        return <main className="p-6">Loading reconciliation account...</main>;
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

    if (!detail) {
        return (
            <main className="p-6">
                <div className="rounded-md border border-red-200 bg-red-50 p-4 text-red-700">
                    Unable to load reconciliation details for this account.
                </div>
            </main>
        );
    }

    return (
        <main className="space-y-6 p-6">
            <div>
                <h1 className="text-2xl font-semibold text-white">Account Reconciliation</h1>
                <p className="text-sm text-zinc-400">
                    Reviewing {detail.accountName} for {detail.focusMonth}.
                </p>
            </div>

            <div className="rounded-xl border border-zinc-800 bg-zinc-900 p-5">
                <div className="flex flex-col gap-5 lg:flex-row lg:items-start lg:justify-between">
                    <div className="space-y-2">
                        <div className="flex flex-wrap items-center gap-2">
                            <h2 className="text-xl font-semibold text-white">{detail.accountName}</h2>
                            <span className="rounded-full border border-red-500/30 bg-red-500/10 px-2 py-1 text-xs font-medium text-red-300">
                                {detail.session?.status === "COMPLETED"
                                    ? "Reconciled"
                                    : detail.session
                                      ? "In Progress"
                                      : "Needs Reconciliation"}
                            </span>
                        </div>

                        <div className="grid gap-3 text-sm text-zinc-300 md:grid-cols-2 xl:grid-cols-4">
                            <p>
                                <span className="text-zinc-500">Institution:</span>{" "}
                                {detail.institutionName || "Not provided"}
                            </p>
                            <p>
                                <span className="text-zinc-500">Type:</span> {detail.accountType}
                            </p>
                            <p>
                                <span className="text-zinc-500">Currency:</span> {detail.currency}
                            </p>
                            <p>
                                <span className="text-zinc-500">Posted Transactions:</span>{" "}
                                {detail.postedTransactionCount}
                            </p>
                        </div>

                        <p className="text-sm text-zinc-400">{detail.statusMessage}</p>
                    </div>

                    <div className="flex min-w-[260px] flex-col gap-3">
                        {detail.canStartReconciliation ? (
                            <button
                                onClick={handleStart}
                                disabled={starting}
                                className="rounded-md bg-red-500 px-4 py-2 text-sm font-medium text-white hover:bg-red-600 transition disabled:opacity-50"
                            >
                                {starting ? "Starting..." : "Start Reconciliation"}
                            </button>
                        ) : null}

                        {detail.canCompleteReconciliation ? (
                            <button
                                onClick={handleComplete}
                                disabled={completing}
                                className="rounded-md border border-zinc-700 px-4 py-2 text-sm font-medium text-white hover:bg-zinc-950 transition disabled:opacity-50"
                            >
                                {completing ? "Completing..." : "Complete Reconciliation"}
                            </button>
                        ) : null}

                        <button
                            type="button"
                            onClick={() => router.refresh()}
                            className="rounded-md border border-zinc-700 px-4 py-2 text-sm font-medium text-zinc-300 hover:bg-zinc-950 transition"
                        >
                            Refresh
                        </button>
                    </div>
                </div>
            </div>

            <div className="grid gap-4 md:grid-cols-4">
                <div className="rounded-xl border border-zinc-800 bg-zinc-900 p-4">
                    <p className="text-sm text-zinc-400">Opening Balance</p>
                    <p className="mt-2 text-2xl font-semibold text-white">
                        {detail.session ? `$${Number(detail.session.openingBalance).toFixed(2)}` : "-"}
                    </p>
                </div>

                <div className="rounded-xl border border-zinc-800 bg-zinc-900 p-4">
                    <p className="text-sm text-zinc-400">Statement Ending Balance</p>
                    <p className="mt-2 text-2xl font-semibold text-white">
                        {detail.session
                            ? `$${Number(detail.session.statementEndingBalance).toFixed(2)}`
                            : "-"}
                    </p>
                </div>

                <div className="rounded-xl border border-zinc-800 bg-zinc-900 p-4">
                    <p className="text-sm text-zinc-400">Book Ending Balance</p>
                    <p className="mt-2 text-2xl font-semibold text-white">
                        {detail.bookEndingBalance != null
                            ? `$${Number(detail.bookEndingBalance).toFixed(2)}`
                            : "-"}
                    </p>
                </div>

                <div className="rounded-xl border border-zinc-800 bg-zinc-900 p-4">
                    <p className="text-sm text-zinc-400">Variance</p>
                    <p className="mt-2 text-2xl font-semibold text-white">
                        {detail.varianceAmount != null
                            ? `$${Number(detail.varianceAmount).toFixed(2)}`
                            : "-"}
                    </p>
                </div>
            </div>

            {detail.canStartReconciliation ? (
                <div className="rounded-xl border border-zinc-800 bg-zinc-900 p-5">
                    <p className="text-sm text-zinc-400">Statement Balances</p>
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
                    </div>
                </div>
            ) : null}

            <div className="rounded-xl border border-zinc-800 bg-zinc-900 p-5">
                <div className="flex items-center justify-between">
                    <div>
                        <p className="text-sm text-zinc-400">Period Transactions</p>
                        <h2 className="mt-1 text-xl font-semibold text-white">
                            Activity for {detail.focusMonth}
                        </h2>
                    </div>
                    <span className="rounded-full border border-zinc-700 px-3 py-1 text-xs text-zinc-400">
                        {detail.transactions.length} items
                    </span>
                </div>

                {detail.transactions.length === 0 ? (
                    <p className="mt-4 text-sm text-zinc-400">
                        No transaction activity was recorded for this account in the selected month.
                    </p>
                ) : (
                    <div className="mt-4 space-y-4">
                        {detail.transactions.map((transaction) => (
                            <div
                                key={transaction.transactionId}
                                className="rounded-lg border border-zinc-800 bg-black p-5"
                            >
                                <div className="flex flex-col gap-4 lg:flex-row lg:items-center lg:justify-between">
                                    <div className="space-y-2">
                                        <div className="flex flex-wrap items-center gap-2">
                                            <h3 className="text-lg font-semibold text-white">
                                                {transaction.merchant || "Unlabeled transaction"}
                                            </h3>
                                            <span className="rounded-full border border-zinc-700 px-2 py-1 text-xs text-zinc-300">
                                                {transaction.status}
                                            </span>
                                        </div>

                                        <div className="grid gap-3 text-sm text-zinc-300 md:grid-cols-2 xl:grid-cols-4">
                                            <p>
                                                <span className="text-zinc-500">Date:</span>{" "}
                                                {transaction.transactionDate}
                                            </p>
                                            <p>
                                                <span className="text-zinc-500">Amount:</span> $
                                                {Number(transaction.amount).toFixed(2)}
                                            </p>
                                            <p>
                                                <span className="text-zinc-500">Memo:</span>{" "}
                                                {transaction.memo || "-"}
                                            </p>
                                            <p>
                                                <span className="text-zinc-500">Transaction ID:</span>{" "}
                                                {transaction.transactionId}
                                            </p>
                                        </div>
                                    </div>
                                </div>
                            </div>
                        ))}
                    </div>
                )}
            </div>

            <div>
                <Link
                    href="/reconciliation"
                    className="text-sm text-zinc-400 hover:text-white transition"
                >
                    ← Back to Reconciliation Overview
                </Link>
            </div>
        </main>
    );
}
