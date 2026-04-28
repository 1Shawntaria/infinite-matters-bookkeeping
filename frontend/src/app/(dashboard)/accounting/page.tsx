"use client";

import { useQuery, useQueryClient } from "@tanstack/react-query";
import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { Suspense, useMemo, useState } from "react";
import { listLedgerAccounts, LedgerAccountReference } from "@/lib/api/accounting";
import {
    FinancialAccount,
    listFinancialAccounts,
    updateFinancialAccount,
} from "@/lib/api/accounts";
import { useOrganizationSession } from "@/lib/auth/session";
import {
    LoadingPanel,
    NextStepsList,
    PageHero,
    SectionBand,
    StatusBanner,
    SummaryMetric,
} from "@/components/app-surfaces";

function AccountingPageContent() {
    const queryClient = useQueryClient();
    const searchParams = useSearchParams();
    const initialCodeFilter = searchParams.get("accountCode") ?? "";
    const { organizationId, hydrated } = useOrganizationSession();
    const [search, setSearch] = useState(initialCodeFilter);
    const [classificationFilter, setClassificationFilter] = useState("ALL");
    const [accountMessage, setAccountMessage] = useState("");
    const [accountError, setAccountError] = useState("");
    const [savingAccountId, setSavingAccountId] = useState("");
    const [accountDrafts, setAccountDrafts] = useState<
        Record<string, { name: string; institutionName: string; active: boolean }>
    >({});

    const accountsQuery = useQuery<LedgerAccountReference[], Error>({
        queryKey: ["ledgerAccounts", organizationId],
        enabled: hydrated && Boolean(organizationId),
        queryFn: () => listLedgerAccounts(organizationId),
    });
    const financialAccountsQuery = useQuery<FinancialAccount[], Error>({
        queryKey: ["financialAccounts", organizationId],
        enabled: hydrated && Boolean(organizationId),
        queryFn: () => listFinancialAccounts(organizationId),
    });

    const loading =
        hydrated && organizationId
            ? accountsQuery.isLoading || financialAccountsQuery.isLoading
            : false;
    const queryError =
        accountsQuery.error?.message ??
        financialAccountsQuery.error?.message ??
        "";
    const accounts = useMemo(() => accountsQuery.data ?? [], [accountsQuery.data]);
    const financialAccounts = useMemo(
        () => financialAccountsQuery.data ?? [],
        [financialAccountsQuery.data]
    );
    const classifications = Array.from(
        new Set(accounts.map((account) => account.classification))
    );
    const filteredAccounts = useMemo(() => {
        const normalizedSearch = search.trim().toLowerCase();
        return accounts.filter((account) => {
            const matchesClassification =
                classificationFilter === "ALL" ||
                account.classification === classificationFilter;
            const matchesSearch =
                normalizedSearch.length === 0 ||
                account.accountCode.toLowerCase().includes(normalizedSearch) ||
                account.accountName.toLowerCase().includes(normalizedSearch) ||
                account.categoryHints.some((hint) =>
                    hint.toLowerCase().includes(normalizedSearch)
                );
            return matchesClassification && matchesSearch;
        });
    }, [accounts, classificationFilter, search]);

    function accountDraft(account: FinancialAccount) {
        return (
            accountDrafts[account.id] ?? {
                name: account.name,
                institutionName: account.institutionName ?? "",
                active: account.active,
            }
        );
    }

    function updateAccountDraft(
        accountId: string,
        field: "name" | "institutionName" | "active",
        value: string | boolean
    ) {
        setAccountDrafts((current) => ({
            ...current,
            [accountId]: {
                ...current[accountId],
                [field]: value,
            } as { name: string; institutionName: string; active: boolean },
        }));
    }

    async function saveAccount(account: FinancialAccount) {
        if (!organizationId) return;
        const draft = accountDraft(account);
        setSavingAccountId(account.id);
        setAccountError("");
        setAccountMessage("");

        try {
            await updateFinancialAccount(account.id, {
                organizationId,
                name: draft.name.trim(),
                institutionName: draft.institutionName.trim(),
                active: draft.active,
            });
            await queryClient.invalidateQueries({
                queryKey: ["financialAccounts", organizationId],
            });
            await queryClient.invalidateQueries({
                queryKey: ["ledgerAccounts", organizationId],
            });
            setAccountMessage(`${draft.name.trim()} updated successfully.`);
        } catch (error) {
            setAccountError(
                error instanceof Error
                    ? error.message
                    : "Unable to update account."
            );
        } finally {
            setSavingAccountId("");
        }
    }

    if (!hydrated || loading) {
        return (
            <LoadingPanel
                title="Loading accounting map."
                message="Gathering system accounts, financial account mappings, and ledger activity into one reference workspace."
            />
        );
    }

    if (!organizationId || queryError) {
        return (
            <main className="p-4 sm:p-6 lg:p-8">
                <StatusBanner
                    tone="error"
                    title="Accounting workspace unavailable"
                    message={queryError || "No organization ID found. Please sign in again."}
                />
            </main>
        );
    }

    return (
        <main className="space-y-8 p-4 sm:p-6 lg:p-8">
            <PageHero
                eyebrow="Accounting reference"
                title="Chart of Accounts"
                description="Use this workspace to understand which accounts exist in the system, which ones are actively being used, and where transactions are likely to land in the books."
                aside={
                    <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-1">
                        <SummaryMetric
                            label="Accounts in map"
                            value={`${accounts.length}`}
                            detail="Includes system category mappings, financial accounts, and accounts seen in ledger activity."
                        />
                        <SummaryMetric
                            label="Visible results"
                            value={`${filteredAccounts.length}`}
                            detail="Use search or account class filters to narrow the accounting picture."
                        />
                    </div>
                }
            />

            <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
                <SummaryMetric
                    label="Assets"
                    value={`${accounts.filter((account) => account.classification === "ASSET").length}`}
                    detail="Cash, bank, receivable, and prepaid-style balances."
                />
                <SummaryMetric
                    label="Liabilities"
                    value={`${accounts.filter((account) => account.classification === "LIABILITY").length}`}
                    detail="Cards, loans, payables, and accrual-style obligations."
                />
                <SummaryMetric
                    label="Revenue"
                    value={`${accounts.filter((account) => account.classification === "REVENUE").length}`}
                    detail="Income accounts tied to sales or operating inflows."
                />
                <SummaryMetric
                    label="Expenses"
                    value={`${accounts.filter((account) => account.classification === "EXPENSE").length}`}
                    detail="Operating expense accounts used during categorization and close."
                />
            </div>

            <SectionBand
                eyebrow="Filters"
                title="Find the account you need"
                description="Search by code, account name, or category hint before you trace transactions or build an adjustment."
            >
                <div className="grid gap-4 lg:grid-cols-[1.2fr_0.8fr]">
                    <label className="space-y-2 text-sm text-zinc-300">
                        <span>Search code, name, or category hint</span>
                        <input
                            value={search}
                            onChange={(event) => setSearch(event.target.value)}
                            className="w-full rounded-md border border-zinc-700 bg-zinc-950 px-3 py-2 text-white outline-none"
                            placeholder="Search 6120, Software and Subscriptions, TRAVEL..."
                        />
                    </label>
                    <label className="space-y-2 text-sm text-zinc-300">
                        <span>Classification</span>
                        <select
                            value={classificationFilter}
                            onChange={(event) => setClassificationFilter(event.target.value)}
                            className="w-full rounded-md border border-zinc-700 bg-zinc-950 px-3 py-2 text-white outline-none"
                        >
                            <option value="ALL">All classifications</option>
                            {classifications.map((classification) => (
                                <option key={classification} value={classification}>
                                    {classification}
                                </option>
                            ))}
                        </select>
                    </label>
                </div>
            </SectionBand>

            <SectionBand
                eyebrow="Account map"
                title={
                    filteredAccounts.length > 0
                        ? "Active chart-of-accounts reference"
                        : "No accounts match the current filters"
                }
                description="This is the bridge between workflow operations and the actual accounting structure behind them."
            >
                {filteredAccounts.length === 0 ? (
                    <div className="grid gap-4 lg:grid-cols-[0.95fr_1.05fr]">
                        <StatusBanner
                            tone="muted"
                            title="No matching accounts"
                            message="Try clearing filters or searching with a broader term. The chart includes both system mappings and accounts already seen in journal activity."
                        />
                        <NextStepsList
                            title="Where this helps most"
                            items={[
                                "Use account codes here before posting an adjustment during close.",
                                "Follow posted transactions from the transactions workspace into the right account family.",
                                "Spot whether an account is just configured or already carrying real ledger activity.",
                            ]}
                        />
                    </div>
                ) : (
                    <div className="space-y-3">
                        {filteredAccounts.map((account) => (
                            <div
                                key={`${account.accountCode}-${account.accountName}`}
                                className="rounded-lg border border-white/10 bg-white/[0.03] p-4"
                            >
                                <div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
                                    <div className="space-y-2">
                                        <div className="flex flex-wrap items-center gap-2">
                                            <h3 className="text-lg font-semibold text-white">
                                                {account.accountCode} · {account.accountName}
                                            </h3>
                                            <span className="rounded-full border border-white/10 bg-white/[0.04] px-2 py-1 text-xs text-zinc-200">
                                                {account.classification}
                                            </span>
                                        </div>
                                        <div className="grid gap-2 text-sm text-zinc-300 md:grid-cols-2 xl:grid-cols-4">
                                            <p>
                                                <span className="text-zinc-500">Sources:</span>{" "}
                                                {account.sourceKinds.join(", ")}
                                            </p>
                                            <p>
                                                <span className="text-zinc-500">Hints:</span>{" "}
                                                {account.categoryHints.length > 0
                                                    ? account.categoryHints.join(", ")
                                                    : "-"}
                                            </p>
                                            <p>
                                                <span className="text-zinc-500">Entries:</span>{" "}
                                                {account.activityEntryCount}
                                            </p>
                                            <p>
                                                <span className="text-zinc-500">Last used:</span>{" "}
                                                {account.lastEntryDate ?? "Not yet posted"}
                                            </p>
                                        </div>
                                    </div>
                                    <div className="min-w-[240px] rounded-lg border border-white/10 bg-black/20 px-4 py-3 text-sm text-zinc-300">
                                        <p>
                                            <span className="text-zinc-500">Debit total:</span>{" "}
                                            ${Number(account.debitTotal).toFixed(2)}
                                        </p>
                                        <p className="mt-2">
                                            <span className="text-zinc-500">Credit total:</span>{" "}
                                            ${Number(account.creditTotal).toFixed(2)}
                                        </p>
                                        <div className="mt-4 flex flex-wrap gap-2">
                                            <Link
                                                href={`/transactions?accountCode=${encodeURIComponent(account.accountCode)}`}
                                                className="rounded-md border border-white/10 px-3 py-2 text-xs text-zinc-200 hover:bg-white/[0.05]"
                                            >
                                                View related transactions
                                            </Link>
                                            <Link
                                                href={`/close?accountCode=${encodeURIComponent(account.accountCode)}`}
                                                className="rounded-md border border-white/10 px-3 py-2 text-xs text-zinc-200 hover:bg-white/[0.05]"
                                            >
                                                Use in close
                                            </Link>
                                            <Link
                                                href="/activity"
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

            <SectionBand
                eyebrow="Account operations"
                title="Manage financial accounts"
                description="Keep account names, institutions, and active status clean so imports, reconciliation, and close all speak the same language."
            >
                {accountError ? (
                    <div className="mb-4">
                        <StatusBanner
                            tone="error"
                            title="Account update failed"
                            message={accountError}
                        />
                    </div>
                ) : null}
                {accountMessage ? (
                    <div className="mb-4">
                        <StatusBanner
                            tone="success"
                            title="Account updated"
                            message={accountMessage}
                        />
                    </div>
                ) : null}
                <div className="space-y-3">
                    {financialAccounts.map((account) => {
                        const draft = accountDraft(account);
                        return (
                            <div
                                key={account.id}
                                className="rounded-lg border border-white/10 bg-white/[0.03] p-4"
                            >
                                <div className="grid gap-4 lg:grid-cols-[1fr_1fr_auto] lg:items-end">
                                    <label className="space-y-2 text-sm text-zinc-300">
                                        <span>Account name</span>
                                        <input
                                            value={draft.name}
                                            onChange={(event) =>
                                                updateAccountDraft(
                                                    account.id,
                                                    "name",
                                                    event.target.value
                                                )
                                            }
                                            className="w-full rounded-md border border-zinc-700 bg-zinc-950 px-3 py-2 text-white outline-none"
                                        />
                                    </label>
                                    <label className="space-y-2 text-sm text-zinc-300">
                                        <span>Institution</span>
                                        <input
                                            value={draft.institutionName}
                                            onChange={(event) =>
                                                updateAccountDraft(
                                                    account.id,
                                                    "institutionName",
                                                    event.target.value
                                                )
                                            }
                                            className="w-full rounded-md border border-zinc-700 bg-zinc-950 px-3 py-2 text-white outline-none"
                                        />
                                    </label>
                                    <div className="flex flex-wrap items-center gap-3">
                                        <label className="flex items-center gap-2 text-sm text-zinc-300">
                                            <input
                                                type="checkbox"
                                                checked={draft.active}
                                                onChange={(event) =>
                                                    updateAccountDraft(
                                                        account.id,
                                                        "active",
                                                        event.target.checked
                                                    )
                                                }
                                            />
                                            Active
                                        </label>
                                        <button
                                            type="button"
                                            onClick={() => saveAccount(account)}
                                            disabled={savingAccountId === account.id}
                                            className="rounded-md bg-emerald-300 px-4 py-2 text-sm font-semibold text-black disabled:opacity-50"
                                        >
                                            {savingAccountId === account.id
                                                ? "Saving..."
                                                : "Save changes"}
                                        </button>
                                    </div>
                                </div>
                                <div className="mt-3 flex flex-wrap gap-2 text-xs text-zinc-400">
                                    <span>{account.accountType}</span>
                                    <span>·</span>
                                    <span>{account.currency}</span>
                                    <span>·</span>
                                    <Link
                                        href={`/transactions?accountCode=${encodeURIComponent(
                                            account.accountType === "BANK"
                                                ? "1000"
                                                : account.accountType === "CREDIT_CARD"
                                                  ? "2000"
                                                  : account.accountType === "CASH"
                                                    ? "1010"
                                                    : "2300"
                                        )}`}
                                        className="text-zinc-300 hover:text-white"
                                    >
                                        Follow activity
                                    </Link>
                                </div>
                            </div>
                        );
                    })}
                </div>
            </SectionBand>
        </main>
    );
}

export default function AccountingPage() {
    return (
        <Suspense
            fallback={
                <LoadingPanel
                    title="Loading accounting map."
                    message="Gathering system accounts, financial account mappings, and ledger activity into one reference workspace."
                />
            }
        >
            <AccountingPageContent />
        </Suspense>
    );
}
