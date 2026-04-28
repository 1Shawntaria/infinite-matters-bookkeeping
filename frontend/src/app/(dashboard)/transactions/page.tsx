"use client";

import { useQuery } from "@tanstack/react-query";
import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { Suspense, useMemo, useState } from "react";
import { listFinancialAccounts, FinancialAccount } from "@/lib/api/accounts";
import { listTransactions } from "@/lib/api/transactions";
import { ImportedTransactionSummary } from "@/lib/api/imports";
import { useOrganizationSession } from "@/lib/auth/session";
import {
    LoadingPanel,
    NextStepsList,
    PageHero,
    SectionBand,
    StatusBanner,
    SummaryMetric,
} from "@/components/app-surfaces";

function TransactionsPageContent() {
    const searchParams = useSearchParams();
    const accountCodeParam = searchParams.get("accountCode") ?? "";
    const { organizationId, hydrated } = useOrganizationSession();
    const [search, setSearch] = useState(accountCodeParam);
    const [statusFilter, setStatusFilter] = useState("ALL");
    const [accountFilter, setAccountFilter] = useState("ALL");

    const transactionsQuery = useQuery<ImportedTransactionSummary[], Error>({
        queryKey: ["transactions", organizationId],
        enabled: hydrated && Boolean(organizationId),
        queryFn: () => listTransactions(organizationId),
    });
    const accountsQuery = useQuery<FinancialAccount[], Error>({
        queryKey: ["financialAccounts", organizationId],
        enabled: hydrated && Boolean(organizationId),
        queryFn: () => listFinancialAccounts(organizationId),
    });

    const loading =
        hydrated && organizationId
            ? transactionsQuery.isLoading || accountsQuery.isLoading
            : false;
    const queryError =
        transactionsQuery.error?.message ?? accountsQuery.error?.message ?? "";
    const transactions = useMemo(
        () => transactionsQuery.data ?? [],
        [transactionsQuery.data]
    );
    const accounts = useMemo(
        () => accountsQuery.data ?? [],
        [accountsQuery.data]
    );

    const filteredTransactions = useMemo(() => {
        const normalizedSearch = search.trim().toLowerCase();
        return transactions.filter((transaction) => {
            const matchesStatus =
                statusFilter === "ALL" || transaction.status === statusFilter;
            const matchesAccount =
                accountFilter === "ALL" ||
                transaction.financialAccountId === accountFilter;
            const matchesSearch =
                normalizedSearch.length === 0 ||
                transaction.ledgerAccountCode?.toLowerCase().includes(normalizedSearch) ||
                transaction.ledgerAccountName?.toLowerCase().includes(normalizedSearch) ||
                transaction.merchant?.toLowerCase().includes(normalizedSearch) ||
                transaction.memo?.toLowerCase().includes(normalizedSearch) ||
                transaction.financialAccountName.toLowerCase().includes(normalizedSearch) ||
                transaction.proposedCategory?.toLowerCase().includes(normalizedSearch) ||
                transaction.finalCategory?.toLowerCase().includes(normalizedSearch);
            return matchesStatus && matchesAccount && matchesSearch;
        });
    }, [accountFilter, search, statusFilter, transactions]);

    const reviewRequiredCount = transactions.filter(
        (transaction) => transaction.status === "REVIEW_REQUIRED"
    ).length;
    const postedCount = transactions.filter(
        (transaction) => transaction.status === "POSTED"
    ).length;
    const totalAmount = filteredTransactions.reduce(
        (sum, transaction) => sum + Number(transaction.amount ?? 0),
        0
    );
    const activeStatuses = Array.from(
        new Set(transactions.map((transaction) => transaction.status))
    );

    if (!hydrated || loading) {
        return (
            <LoadingPanel
                title="Loading transactions workspace."
                message="Pulling imported activity, account context, and posting status into one investigation surface."
            />
        );
    }

    if (!organizationId || queryError) {
        return (
            <main className="p-4 sm:p-6 lg:p-8">
                <StatusBanner
                    tone="error"
                    title="Transactions unavailable"
                    message={queryError || "No organization ID found. Please sign in again."}
                />
            </main>
        );
    }

    return (
        <main className="space-y-8 p-4 sm:p-6 lg:p-8">
            <PageHero
                eyebrow="Transaction investigation"
                title="Transactions"
                description="Search across imported activity, inspect posting outcomes, and spot what still needs review before it slows down close."
                aside={
                    <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-1">
                        <SummaryMetric
                            label="Transactions"
                            value={`${transactions.length}`}
                            detail="All imported rows currently in the workspace."
                        />
                        <SummaryMetric
                            label="Needs review"
                            value={`${reviewRequiredCount}`}
                            detail="These items still need a final category decision."
                            tone={reviewRequiredCount > 0 ? "warning" : "success"}
                        />
                    </div>
                }
            />

            <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
                <SummaryMetric
                    label="Posted"
                    value={`${postedCount}`}
                    detail="Transactions already posted to the ledger."
                    tone={postedCount > 0 ? "success" : "default"}
                />
                <SummaryMetric
                    label="Filtered total"
                    value={`$${totalAmount.toFixed(2)}`}
                    detail="Total amount across the currently visible result set."
                />
                <SummaryMetric
                    label="Tracked accounts"
                    value={`${accounts.length}`}
                    detail="Use account filters to isolate one bank or card feed."
                />
                <SummaryMetric
                    label="Statuses in play"
                    value={`${activeStatuses.length}`}
                    detail="Imported, review, and posted states stay visible together."
                />
            </div>

            <SectionBand
                eyebrow="Filters"
                title="Find the transaction story quickly"
                description="Narrow by account, posting state, or merchant clues before you jump into review or reconciliation."
            >
                <div className="grid gap-4 lg:grid-cols-[1.3fr_0.7fr_0.7fr]">
                    <label className="space-y-2 text-sm text-zinc-300">
                        <span>Search merchant, memo, category, or account</span>
                        <input
                            value={search}
                            onChange={(event) => setSearch(event.target.value)}
                            className="w-full rounded-md border border-zinc-700 bg-zinc-950 px-3 py-2 text-white outline-none"
                            placeholder="Search CLOUDCO, UNKNOWN VENDOR, SOFTWARE..."
                        />
                    </label>
                    <label className="space-y-2 text-sm text-zinc-300">
                        <span>Status</span>
                        <select
                            value={statusFilter}
                            onChange={(event) => setStatusFilter(event.target.value)}
                            className="w-full rounded-md border border-zinc-700 bg-zinc-950 px-3 py-2 text-white outline-none"
                        >
                            <option value="ALL">All statuses</option>
                            {activeStatuses.map((status) => (
                                <option key={status} value={status}>
                                    {status.replaceAll("_", " ")}
                                </option>
                            ))}
                        </select>
                    </label>
                    <label className="space-y-2 text-sm text-zinc-300">
                        <span>Account</span>
                        <select
                            value={accountFilter}
                            onChange={(event) => setAccountFilter(event.target.value)}
                            className="w-full rounded-md border border-zinc-700 bg-zinc-950 px-3 py-2 text-white outline-none"
                        >
                            <option value="ALL">All accounts</option>
                            {accounts.map((account) => (
                                <option key={account.id} value={account.id}>
                                    {account.name}
                                </option>
                            ))}
                        </select>
                    </label>
                </div>
            </SectionBand>

            <SectionBand
                eyebrow="Results"
                title={
                    filteredTransactions.length > 0
                        ? "Transaction results"
                        : "No transactions match the current filters"
                }
                description="Use this view to trace what imported, what got posted, and what still needs a judgment call."
            >
                {filteredTransactions.length === 0 ? (
                    <div className="grid gap-4 lg:grid-cols-[0.95fr_1.05fr]">
                        <StatusBanner
                            tone="muted"
                            title="No matching transactions"
                            message="Try widening the status or account filters, or clear the search term to get back to the full operational picture."
                        />
                        <NextStepsList
                            title="Useful follow-up moves"
                            items={[
                                "Open the review queue if you are trying to find unresolved categorization work.",
                                "Use setup and import to bring in another statement if the workspace is still sparse.",
                                "Open reconciliation when you want account-level balance context instead of row-level activity.",
                            ]}
                        />
                    </div>
                ) : (
                    <div className="space-y-3">
                        {filteredTransactions.map((transaction) => (
                            <div
                                key={transaction.transactionId}
                                className="rounded-lg border border-white/10 bg-white/[0.03] p-4"
                            >
                                <div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
                                    <div className="space-y-2">
                                        <div className="flex flex-wrap items-center gap-2">
                                            <h3 className="text-lg font-semibold text-white">
                                                {transaction.merchant || "Imported transaction"}
                                            </h3>
                                            <span className="rounded-full border border-white/10 bg-white/[0.04] px-2 py-1 text-xs text-zinc-200">
                                                {transaction.status.replaceAll("_", " ")}
                                            </span>
                                            <span className="rounded-full border border-white/10 bg-white/[0.04] px-2 py-1 text-xs text-zinc-300">
                                                {transaction.route}
                                            </span>
                                        </div>
                                        <p className="text-sm text-zinc-400">
                                            {transaction.memo || "No memo captured from the import source."}
                                        </p>
                                        <div className="grid gap-2 text-sm text-zinc-300 md:grid-cols-2 xl:grid-cols-4">
                                            <p>
                                                <span className="text-zinc-500">Account:</span>{" "}
                                                {transaction.financialAccountName}
                                            </p>
                                            <p>
                                                <span className="text-zinc-500">Date:</span>{" "}
                                                {transaction.transactionDate}
                                            </p>
                                            <p>
                                                <span className="text-zinc-500">Amount:</span>{" "}
                                                ${Number(transaction.amount).toFixed(2)} {transaction.currency}
                                            </p>
                                            <p>
                                                <span className="text-zinc-500">Imported:</span>{" "}
                                                {new Date(transaction.importedAt).toLocaleDateString("en-US", {
                                                    month: "short",
                                                    day: "numeric",
                                                })}
                                            </p>
                                            <p>
                                                <span className="text-zinc-500">Proposed:</span>{" "}
                                                {transaction.proposedCategory ?? "-"}
                                            </p>
                                            <p>
                                                <span className="text-zinc-500">Final:</span>{" "}
                                                {transaction.finalCategory ?? "Pending review"}
                                            </p>
                                            <p>
                                                <span className="text-zinc-500">Ledger account:</span>{" "}
                                                {transaction.ledgerAccountCode && transaction.ledgerAccountName
                                                    ? `${transaction.ledgerAccountCode} · ${transaction.ledgerAccountName}`
                                                    : "-"}
                                            </p>
                                            <p>
                                                <span className="text-zinc-500">Confidence:</span>{" "}
                                                {Math.round((transaction.confidenceScore ?? 0) * 100)}%
                                            </p>
                                            <p>
                                                <span className="text-zinc-500">MCC:</span>{" "}
                                                {transaction.mcc ?? "-"}
                                            </p>
                                        </div>
                                    </div>
                                    <div className="min-w-[220px] rounded-lg border border-white/10 bg-black/20 px-4 py-3">
                                        <p className="text-xs uppercase tracking-[0.18em] text-zinc-500">
                                            Recommended follow-up
                                        </p>
                                        <p className="mt-2 text-sm font-semibold text-white">
                                            {transaction.status === "REVIEW_REQUIRED"
                                                ? "Send to review queue"
                                                : transaction.status === "POSTED"
                                                  ? "Trace in ledger / close"
                                                  : "Watch posting outcome"}
                                        </p>
                                        <p className="mt-2 text-sm text-zinc-400">
                                            {transaction.status === "REVIEW_REQUIRED"
                                                ? "This row still needs a human category decision before the books are fully clean."
                                                : transaction.status === "POSTED"
                                                  ? "This row is already in the ledger, so use close controls when you need the accounting context."
                                                  : "This row is imported and waiting on the rest of the workflow to finish."}
                                        </p>
                                        <div className="mt-4 flex flex-wrap gap-2">
                                            {transaction.status === "REVIEW_REQUIRED" ? (
                                                <Link
                                                    href="/review-queue"
                                                    className="rounded-md border border-white/10 px-3 py-2 text-xs text-zinc-200 hover:bg-white/[0.05]"
                                                >
                                                    Open review queue
                                                </Link>
                                            ) : null}
                                            {transaction.ledgerAccountCode ? (
                                                <Link
                                                    href={`/accounting?accountCode=${encodeURIComponent(transaction.ledgerAccountCode)}`}
                                                    className="rounded-md border border-white/10 px-3 py-2 text-xs text-zinc-200 hover:bg-white/[0.05]"
                                                >
                                                    View {transaction.ledgerAccountCode}
                                                </Link>
                                            ) : null}
                                            {transaction.status === "POSTED" ? (
                                                <Link
                                                    href="/close"
                                                    className="rounded-md border border-white/10 px-3 py-2 text-xs text-zinc-200 hover:bg-white/[0.05]"
                                                >
                                                    Open close workspace
                                                </Link>
                                            ) : null}
                                            <Link
                                                href={`/activity?lane=IMPORT&entityId=${encodeURIComponent(transaction.transactionId)}&label=${encodeURIComponent(transaction.merchant || "transaction activity")}`}
                                                className="rounded-md border border-white/10 px-3 py-2 text-xs text-zinc-200 hover:bg-white/[0.05]"
                                            >
                                                Open activity trail
                                            </Link>
                                        </div>
                                    </div>
                                </div>
                            </div>
                        ))}
                    </div>
                )}
            </SectionBand>
        </main>
    );
}

export default function TransactionsPage() {
    return (
        <Suspense
            fallback={
                <LoadingPanel
                    title="Loading transactions workspace."
                    message="Pulling imported activity, account context, and posting status into one investigation surface."
                />
            }
        >
            <TransactionsPageContent />
        </Suspense>
    );
}
