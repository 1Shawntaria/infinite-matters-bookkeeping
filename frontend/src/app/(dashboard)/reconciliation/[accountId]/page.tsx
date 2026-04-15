"use client";

import { use } from "react";
import Link from "next/link";

type AccountReconciliationPageProps = {
    params: Promise<{
        accountId: string;
    }>;
};

const mockedAccount = {
    name: "Operating Checking",
    institutionName: "Demo Bank",
    accountType: "BANK",
    statementMonth: "2026-03",
    unmatchedItems: 3,
    openingBalance: 1000.0,
    statementEndingBalance: 1128.43,
    bookEndingBalance: 660.82,
};

const mockedTransactions = [
    {
        id: "txn-1",
        merchant: "MISC PAYMENT",
        date: "2026-03-11",
        amount: 127.4,
        proposedCategory: "OTHER",
        status: "NEEDS_REVIEW",
    },
    {
        id: "txn-2",
        merchant: "ONLINE TRANSFER",
        date: "2026-03-12",
        amount: 250.0,
        proposedCategory: "OTHER",
        status: "UNMATCHED",
    },
    {
        id: "txn-3",
        merchant: "UNMAPPED STORE",
        date: "2026-03-14",
        amount: 64.22,
        proposedCategory: "OTHER",
        status: "UNMATCHED",
    },
];

export default function AccountReconciliationPage({
                                                      params,
                                                  }: AccountReconciliationPageProps) {
    const resolvedParams = use(params);

    return (
        <main className="space-y-6 p-6">
            <div>
                <h1 className="text-2xl font-semibold">Account Reconciliation</h1>
                <p className="text-sm text-zinc-400">
                    Reviewing account: {mockedAccount.name} ({resolvedParams.accountId})
                </p>
            </div>

            <div className="rounded-xl border border-zinc-800 bg-zinc-900 p-5">
                <div className="flex flex-col gap-5 lg:flex-row lg:items-center lg:justify-between">
                    <div className="space-y-2">
                        <div className="flex flex-wrap items-center gap-2">
                            <h2 className="text-xl font-semibold text-white">
                                {mockedAccount.name}
                            </h2>
                            <span className="rounded-full border border-red-500/30 bg-red-500/10 px-2 py-1 text-xs font-medium text-red-300">
                                Needs Reconciliation
                            </span>
                        </div>

                        <div className="grid gap-3 text-sm text-zinc-300 md:grid-cols-2 xl:grid-cols-4">
                            <p>
                                <span className="text-zinc-500">Institution:</span>{" "}
                                {mockedAccount.institutionName}
                            </p>
                            <p>
                                <span className="text-zinc-500">Type:</span>{" "}
                                {mockedAccount.accountType}
                            </p>
                            <p>
                                <span className="text-zinc-500">Statement Month:</span>{" "}
                                {mockedAccount.statementMonth}
                            </p>
                            <p>
                                <span className="text-zinc-500">Unmatched Items:</span>{" "}
                                {mockedAccount.unmatchedItems}
                            </p>
                        </div>
                    </div>

                    <div className="flex min-w-[260px] flex-col gap-3">
                        <button className="rounded-md bg-red-500 px-4 py-2 text-sm font-medium text-white hover:bg-red-600 transition">
                            Start Matching
                        </button>

                        <button
                            disabled
                            className="cursor-not-allowed rounded-md border border-zinc-700 px-4 py-2 text-sm font-medium text-zinc-500 opacity-60"
                        >
                            Mark Account Reconciled
                        </button>
                    </div>
                </div>
            </div>

            <div className="grid gap-4 md:grid-cols-3">
                <div className="rounded-xl border border-zinc-800 bg-zinc-900 p-4">
                    <p className="text-sm text-zinc-400">Opening Balance</p>
                    <p className="mt-2 text-2xl font-semibold text-white">
                        ${mockedAccount.openingBalance.toFixed(2)}
                    </p>
                </div>

                <div className="rounded-xl border border-zinc-800 bg-zinc-900 p-4">
                    <p className="text-sm text-zinc-400">Statement Ending Balance</p>
                    <p className="mt-2 text-2xl font-semibold text-white">
                        ${mockedAccount.statementEndingBalance.toFixed(2)}
                    </p>
                </div>

                <div className="rounded-xl border border-zinc-800 bg-zinc-900 p-4">
                    <p className="text-sm text-zinc-400">Book Ending Balance</p>
                    <p className="mt-2 text-2xl font-semibold text-white">
                        ${mockedAccount.bookEndingBalance.toFixed(2)}
                    </p>
                </div>
            </div>

            <div className="rounded-xl border border-zinc-800 bg-zinc-900 p-5">
                <div className="flex items-center justify-between">
                    <div>
                        <p className="text-sm text-zinc-400">Unmatched Transactions</p>
                        <h2 className="mt-1 text-xl font-semibold text-white">
                            Transactions needing reconciliation
                        </h2>
                    </div>
                    <span className="rounded-full border border-zinc-700 px-3 py-1 text-xs text-zinc-400">
                        {mockedTransactions.length} items
                    </span>
                </div>

                <div className="mt-4 space-y-4">
                    {mockedTransactions.map((transaction) => (
                        <div
                            key={transaction.id}
                            className="rounded-lg border border-zinc-800 bg-black p-5"
                        >
                            <div className="flex flex-col gap-5 lg:flex-row lg:items-center lg:justify-between">
                                <div className="space-y-2">
                                    <div className="flex flex-wrap items-center gap-2">
                                        <h3 className="text-lg font-semibold text-white">
                                            {transaction.merchant}
                                        </h3>
                                        <span className="rounded-full border border-amber-500/30 bg-amber-500/10 px-2 py-1 text-xs font-medium text-amber-300">
                                            {transaction.status.replace("_", " ")}
                                        </span>
                                    </div>

                                    <div className="grid gap-3 text-sm text-zinc-300 md:grid-cols-2 xl:grid-cols-4">
                                        <p>
                                            <span className="text-zinc-500">Date:</span>{" "}
                                            {transaction.date}
                                        </p>
                                        <p>
                                            <span className="text-zinc-500">Amount:</span> $
                                            {transaction.amount.toFixed(2)}
                                        </p>
                                        <p>
                                            <span className="text-zinc-500">Proposed Category:</span>{" "}
                                            {transaction.proposedCategory}
                                        </p>
                                        <p>
                                            <span className="text-zinc-500">Transaction ID:</span>{" "}
                                            {transaction.id}
                                        </p>
                                    </div>
                                </div>

                                <div className="flex min-w-[180px] flex-col gap-3">
                                    <button className="rounded-md bg-white px-4 py-2 text-sm font-medium text-black hover:bg-zinc-200 transition">
                                        Review Transaction
                                    </button>
                                    <button className="rounded-md border border-zinc-700 px-4 py-2 text-sm font-medium text-white hover:bg-zinc-900 transition">
                                        Match Manually
                                    </button>
                                </div>
                            </div>
                        </div>
                    ))}
                </div>
            </div>

            <div className="rounded-xl border border-zinc-800 bg-zinc-900 p-5">
                <p className="text-sm text-zinc-400">Reconciliation Guidance</p>
                <ul className="mt-3 list-disc space-y-2 pl-5 text-sm text-zinc-300">
                    <li>Review unmatched transactions first.</li>
                    <li>Confirm whether each item belongs in this account and period.</li>
                    <li>Resolve any categorization issues before marking the account reconciled.</li>
                    <li>When balances align, complete the account reconciliation step.</li>
                </ul>
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