"use client";

import { useQuery } from "@tanstack/react-query";
import { use, useState } from "react";
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import {
    completeReconciliation,
    getReconciliationAccountDetail,
    ReconciliationAccountDetail,
    startReconciliation,
} from "@/lib/api/reconciliation";
import { useOrganizationSession } from "@/lib/auth/session";
import {
    LoadingPanel,
    PageHero,
    SectionBand,
    StatusBanner,
    SummaryMetric,
} from "@/components/app-surfaces";

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
    const { organizationId, hydrated } = useOrganizationSession();
    const [error, setError] = useState("");
    const [openingBalance, setOpeningBalance] = useState("");
    const [statementEndingBalance, setStatementEndingBalance] = useState("");
    const [starting, setStarting] = useState(false);
    const [completing, setCompleting] = useState(false);

    const focusMonth = searchParams.get("month") || undefined;
    const detailQuery = useQuery<ReconciliationAccountDetail, Error>({
        queryKey: ["reconciliationAccountDetail", organizationId, accountId, focusMonth],
        enabled: hydrated && Boolean(organizationId),
        queryFn: async () => {
            const result = await getReconciliationAccountDetail(
                organizationId,
                accountId,
                focusMonth
            );
            setError("");
            return result;
        },
    });
    const detail = organizationId ? (detailQuery.data ?? null) : null;
    const loading = hydrated && organizationId ? detailQuery.isLoading : false;
    const queryError = detailQuery.error?.message ?? "";

    async function refreshDetail() {
        if (!organizationId) return;
        await detailQuery.refetch();
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

    if (!hydrated || loading) {
        return (
            <LoadingPanel
                title="Loading reconciliation account."
                message="Preparing account balances, transactions, and session status."
            />
        );
    }

    if (!organizationId || error || queryError) {
        return (
            <main className="p-4 sm:p-6 lg:p-8">
                <StatusBanner
                    tone="error"
                    title="Account reconciliation unavailable"
                    message={error || queryError || "No organization ID found. Please sign in again."}
                />
            </main>
        );
    }

    if (!detail) {
        return (
            <main className="p-4 sm:p-6 lg:p-8">
                <StatusBanner
                    tone="error"
                    title="Account details unavailable"
                    message="Unable to load reconciliation details for this account."
                />
            </main>
        );
    }

    return (
        <main className="space-y-8 p-4 sm:p-6 lg:p-8">
            <PageHero
                eyebrow="Account reconciliation"
                title={detail.accountName}
                description={`Reviewing ${detail.accountName} for ${detail.focusMonth}.`}
                aside={
                    <div className="flex flex-col gap-3">
                        <Link
                            href="/reconciliation"
                            className="rounded-md border border-zinc-800 px-4 py-2 text-center text-sm text-zinc-300 hover:bg-zinc-950 hover:text-white"
                        >
                            Back to overview
                        </Link>
                        <button
                            type="button"
                            onClick={() => router.refresh()}
                            className="rounded-md border border-zinc-800 px-4 py-2 text-sm text-zinc-300 hover:bg-zinc-950 hover:text-white"
                        >
                            Refresh
                        </button>
                    </div>
                }
            >
                <div className="flex flex-wrap gap-3 text-sm">
                    <span className="rounded-full border border-red-500/30 bg-red-500/10 px-3 py-1 text-red-200">
                        {detail.session?.status === "COMPLETED"
                            ? "Reconciled"
                            : detail.session
                              ? "In Progress"
                              : "Needs Reconciliation"}
                    </span>
                    <span className="rounded-full border border-white/10 bg-white/[0.04] px-3 py-1 text-zinc-300">
                        {detail.institutionName || "Institution not provided"}
                    </span>
                </div>
            </PageHero>

            <SectionBand
                eyebrow="Account status"
                title={detail.statusMessage}
                description="Use the current session status and transaction list below to clear remaining variance and finish the account cleanly."
                actions={
                    <div className="flex min-w-[240px] flex-col gap-3">
                        {detail.canStartReconciliation ? (
                            <button
                                onClick={handleStart}
                                disabled={starting}
                                className="rounded-md bg-red-500 px-4 py-2 text-sm font-medium text-white hover:bg-red-600 disabled:opacity-50"
                            >
                                {starting ? "Starting..." : "Start Reconciliation"}
                            </button>
                        ) : null}

                        {detail.canCompleteReconciliation ? (
                            <button
                                onClick={handleComplete}
                                disabled={completing}
                                className="rounded-md border border-zinc-700 px-4 py-2 text-sm font-medium text-white hover:bg-zinc-950 disabled:opacity-50"
                            >
                                {completing ? "Completing..." : "Complete Reconciliation"}
                            </button>
                        ) : null}
                    </div>
                }
            >
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
            </SectionBand>

            <div className="grid gap-4 md:grid-cols-4">
                <SummaryMetric
                    label="Opening Balance"
                    value={detail.session ? `$${Number(detail.session.openingBalance).toFixed(2)}` : "-"}
                />
                <SummaryMetric
                    label="Statement Ending Balance"
                    value={detail.session ? `$${Number(detail.session.statementEndingBalance).toFixed(2)}` : "-"}
                />
                <SummaryMetric
                    label="Book Ending Balance"
                    value={detail.bookEndingBalance != null ? `$${Number(detail.bookEndingBalance).toFixed(2)}` : "-"}
                />
                <SummaryMetric
                    label="Variance"
                    value={detail.varianceAmount != null ? `$${Number(detail.varianceAmount).toFixed(2)}` : "-"}
                    tone={detail.varianceAmount === 0 ? "success" : "warning"}
                />
            </div>

            {detail.canStartReconciliation ? (
                <div className="rounded-xl border border-zinc-900/80 bg-black/45 p-6 backdrop-blur">
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

            <div className="rounded-xl border border-zinc-900/80 bg-black/45 p-6 backdrop-blur">
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
                                className="rounded-lg border border-zinc-800 bg-zinc-950/80 p-5"
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
        </main>
    );
}
