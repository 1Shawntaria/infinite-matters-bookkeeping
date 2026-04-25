"use client";

import { useQuery, useQueryClient } from "@tanstack/react-query";
import Link from "next/link";
import { useEffect, useMemo, useState } from "react";
import {
    createFinancialAccount,
    FinancialAccount,
    listFinancialAccounts,
} from "@/lib/api/accounts";
import { importTransactionsCsv, ImportBatchResult } from "@/lib/api/imports";
import { useOrganizationSession } from "@/lib/auth/session";
import {
    LoadingPanel,
    NextStepsList,
    PageHero,
    SectionBand,
    StatusBanner,
    SummaryMetric,
} from "@/components/app-surfaces";

const ACCOUNT_TYPE_OPTIONS: Array<FinancialAccount["accountType"]> = [
    "BANK",
    "CREDIT_CARD",
    "CASH",
    "LOAN",
];

export default function SetupPage() {
    const queryClient = useQueryClient();
    const { organizationId, hydrated } = useOrganizationSession();
    const [accountName, setAccountName] = useState("");
    const [accountType, setAccountType] = useState<FinancialAccount["accountType"]>("BANK");
    const [institutionName, setInstitutionName] = useState("");
    const [currency, setCurrency] = useState("USD");
    const [selectedAccountId, setSelectedAccountId] = useState("");
    const [csvFile, setCsvFile] = useState<File | null>(null);
    const [accountError, setAccountError] = useState("");
    const [accountSuccess, setAccountSuccess] = useState("");
    const [importError, setImportError] = useState("");
    const [importSuccess, setImportSuccess] = useState("");
    const [importResult, setImportResult] = useState<ImportBatchResult | null>(null);
    const [creatingAccount, setCreatingAccount] = useState(false);
    const [importing, setImporting] = useState(false);

    const accountsQuery = useQuery<FinancialAccount[], Error>({
        queryKey: ["financialAccounts", organizationId],
        enabled: hydrated && Boolean(organizationId),
        queryFn: () => listFinancialAccounts(organizationId),
    });
    const accounts = useMemo(() => accountsQuery.data ?? [], [accountsQuery.data]);
    const loading = hydrated && organizationId ? accountsQuery.isLoading : false;
    const queryError = accountsQuery.error?.message ?? "";

    useEffect(() => {
        if (!selectedAccountId && accounts[0]?.id) {
            setSelectedAccountId(accounts[0].id);
        }
    }, [accounts, selectedAccountId]);

    const selectedAccount = useMemo(
        () => accounts.find((account) => account.id === selectedAccountId) ?? null,
        [accounts, selectedAccountId]
    );

    async function handleCreateAccount(event: React.FormEvent<HTMLFormElement>) {
        event.preventDefault();
        if (!organizationId) {
            setAccountError("No organization ID found. Please sign in again.");
            return;
        }

        setCreatingAccount(true);
        setAccountError("");
        setAccountSuccess("");

        try {
            const createdAccount = await createFinancialAccount({
                organizationId,
                name: accountName.trim(),
                accountType,
                institutionName: institutionName.trim(),
                currency: currency.trim().toUpperCase(),
            });

            await queryClient.invalidateQueries({ queryKey: ["financialAccounts", organizationId] });
            setSelectedAccountId(createdAccount.id);
            setAccountName("");
            setInstitutionName("");
            setCurrency("USD");
            setAccountSuccess(`${createdAccount.name} is ready for imports.`);
        } catch (err) {
            setAccountError(err instanceof Error ? err.message : "Unable to create account.");
        } finally {
            setCreatingAccount(false);
        }
    }

    async function handleImport(event: React.FormEvent<HTMLFormElement>) {
        event.preventDefault();
        if (!organizationId) {
            setImportError("No organization ID found. Please sign in again.");
            return;
        }
        if (!selectedAccountId) {
            setImportError("Choose an account before importing a CSV.");
            return;
        }
        if (!csvFile) {
            setImportError("Attach a CSV file before starting the import.");
            return;
        }

        setImporting(true);
        setImportError("");
        setImportSuccess("");

        try {
            const result = await importTransactionsCsv(organizationId, selectedAccountId, csvFile);
            setImportResult(result);
            setImportSuccess("Import completed successfully.");
            setCsvFile(null);
            await queryClient.invalidateQueries({ queryKey: ["dashboardSnapshot", organizationId] });
            await queryClient.invalidateQueries({ queryKey: ["reviewTasks", organizationId] });
            await queryClient.invalidateQueries({ queryKey: ["reconciliationDashboard", organizationId] });
        } catch (err) {
            setImportResult(null);
            setImportError(err instanceof Error ? err.message : "Unable to import CSV.");
        } finally {
            setImporting(false);
        }
    }

    if (!hydrated || loading) {
        return (
            <LoadingPanel
                title="Loading setup workspace."
                message="Preparing accounts, import tools, and the fastest path to useful data."
            />
        );
    }

    if (!organizationId || queryError) {
        return (
            <main className="p-4 sm:p-6 lg:p-8">
                <StatusBanner
                    tone="error"
                    title="Setup unavailable"
                    message={queryError || "No organization ID found. Please sign in again."}
                />
            </main>
        );
    }

    return (
        <main className="space-y-8 p-4 sm:p-6 lg:p-8">
            <PageHero
                eyebrow="Workspace setup"
                title="Import your first real activity"
                description="Create a financial account, attach a CSV statement, and let the workspace populate dashboard signals, review tasks, and reconciliation work from real data."
                aside={
                    <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-1">
                        <SummaryMetric
                            label="Active accounts"
                            value={`${accounts.length}`}
                            detail="Each import needs a financial account destination."
                        />
                        <SummaryMetric
                            label="Last import"
                            value={
                                importResult
                                    ? `${importResult.importedCount} new`
                                    : "Not started"
                            }
                            detail="Successful imports immediately update the rest of the workspace."
                            tone={importResult ? "success" : "default"}
                        />
                    </div>
                }
            >
                <div className="flex flex-wrap gap-3">
                    <Link
                        href="/dashboard"
                        className="rounded-md border border-white/10 px-4 py-2 text-sm text-zinc-200 hover:bg-white/[0.05]"
                    >
                        Back to dashboard
                    </Link>
                    <Link
                        href="/review-queue"
                        className="rounded-md border border-white/10 px-4 py-2 text-sm text-zinc-200 hover:bg-white/[0.05]"
                    >
                        Open review queue
                    </Link>
                </div>
            </PageHero>

            <SectionBand
                eyebrow="Quick path"
                title="What gets the workspace useful fastest"
                description="The cleanest first run is account setup, then CSV import, then review queue, then reconciliation."
            >
                <div className="grid gap-4 lg:grid-cols-[1.1fr_0.9fr]">
                    <NextStepsList
                        title="Recommended sequence"
                        items={[
                            "Create the financial account that matches the statement you want to import first.",
                            "Upload a CSV export from the bank or card provider into that account.",
                            "Review any ambiguous merchants in the review queue, then move into reconciliation if balances are available.",
                        ]}
                    />
                    <StatusBanner
                        tone="muted"
                        title="CSV import tip"
                        message="Use the provider export as-is when possible. The import endpoint expects the file in multipart field 'file' and will safely skip duplicates."
                    />
                </div>
            </SectionBand>

            <div className="grid gap-6 xl:grid-cols-[0.95fr_1.05fr]">
                <SectionBand
                    eyebrow="Step 1"
                    title="Create a financial account"
                    description="This account becomes the destination for imports and the anchor for reconciliation."
                >
                    {accountError ? (
                        <div className="mb-4">
                            <StatusBanner
                                tone="error"
                                title="Account setup failed"
                                message={accountError}
                            />
                        </div>
                    ) : null}

                    {accountSuccess ? (
                        <div className="mb-4">
                            <StatusBanner
                                tone="success"
                                title="Account created"
                                message={accountSuccess}
                            />
                        </div>
                    ) : null}

                    <form className="space-y-4" onSubmit={handleCreateAccount}>
                        <div className="grid gap-4 md:grid-cols-2">
                            <label className="space-y-2 text-sm text-zinc-300">
                                <span>Account Name</span>
                                <input
                                    value={accountName}
                                    onChange={(event) => setAccountName(event.target.value)}
                                    className="w-full rounded-md border border-zinc-700 bg-zinc-950 px-3 py-2 text-white outline-none"
                                    placeholder="Operating Checking"
                                    required
                                />
                            </label>

                            <label className="space-y-2 text-sm text-zinc-300">
                                <span>Account Type</span>
                                <select
                                    value={accountType}
                                    onChange={(event) =>
                                        setAccountType(event.target.value as FinancialAccount["accountType"])
                                    }
                                    className="w-full rounded-md border border-zinc-700 bg-zinc-950 px-3 py-2 text-white outline-none"
                                >
                                    {ACCOUNT_TYPE_OPTIONS.map((option) => (
                                        <option key={option} value={option}>
                                            {option.replace("_", " ")}
                                        </option>
                                    ))}
                                </select>
                            </label>
                        </div>

                        <div className="grid gap-4 md:grid-cols-2">
                            <label className="space-y-2 text-sm text-zinc-300">
                                <span>Institution Name</span>
                                <input
                                    value={institutionName}
                                    onChange={(event) => setInstitutionName(event.target.value)}
                                    className="w-full rounded-md border border-zinc-700 bg-zinc-950 px-3 py-2 text-white outline-none"
                                    placeholder="Infinite Matters Bank"
                                />
                            </label>

                            <label className="space-y-2 text-sm text-zinc-300">
                                <span>Currency</span>
                                <input
                                    value={currency}
                                    onChange={(event) => setCurrency(event.target.value.toUpperCase())}
                                    className="w-full rounded-md border border-zinc-700 bg-zinc-950 px-3 py-2 text-white outline-none"
                                    placeholder="USD"
                                    maxLength={3}
                                    required
                                />
                            </label>
                        </div>

                        <button
                            type="submit"
                            disabled={creatingAccount}
                            className="rounded-md bg-emerald-300 px-4 py-2.5 text-sm font-semibold text-black disabled:opacity-50"
                        >
                            {creatingAccount ? "Creating..." : "Create Account"}
                        </button>
                    </form>
                </SectionBand>

                <SectionBand
                    eyebrow="Accounts ready for import"
                    title={accounts.length > 0 ? "Available financial accounts" : "No accounts yet"}
                    description={
                        accounts.length > 0
                            ? "Pick one of these when you import a CSV."
                            : "Create your first account on the left to unlock the import step."
                    }
                >
                    {accounts.length > 0 ? (
                        <div className="space-y-3">
                            {accounts.map((account) => (
                                <div
                                    key={account.id}
                                    className={[
                                        "rounded-lg border px-4 py-4",
                                        selectedAccountId === account.id
                                            ? "border-emerald-400/40 bg-emerald-300/10"
                                            : "border-white/10 bg-white/[0.03]",
                                    ].join(" ")}
                                >
                                    <div className="flex items-center justify-between gap-3">
                                        <div>
                                            <p className="text-sm font-semibold text-white">
                                                {account.name}
                                            </p>
                                            <p className="mt-1 text-xs text-zinc-400">
                                                {(account.institutionName || "Institution not provided")} · {account.accountType} · {account.currency}
                                            </p>
                                        </div>
                                        <button
                                            type="button"
                                            onClick={() => setSelectedAccountId(account.id)}
                                            className="rounded-md border border-white/10 px-3 py-2 text-xs text-zinc-200 hover:bg-white/[0.05]"
                                        >
                                            {selectedAccountId === account.id ? "Selected" : "Use for import"}
                                        </button>
                                    </div>
                                </div>
                            ))}
                        </div>
                    ) : (
                        <StatusBanner
                            tone="muted"
                            title="Waiting on the first account"
                            message="Once an account exists, the import step can route transactions to the right destination and unlock reconciliation."
                        />
                    )}
                </SectionBand>
            </div>

            <SectionBand
                eyebrow="Step 2"
                title="Import a CSV statement"
                description="Pick the destination account, upload the file, and let the app create posted transactions plus review items where needed."
            >
                {importError ? (
                    <div className="mb-4">
                        <StatusBanner
                            tone="error"
                            title="Import failed"
                            message={importError}
                        />
                    </div>
                ) : null}

                {importSuccess ? (
                    <div className="mb-4">
                        <StatusBanner
                            tone="success"
                            title="Import completed"
                            message={importSuccess}
                        />
                    </div>
                ) : null}

                <form className="space-y-4" onSubmit={handleImport}>
                    <div className="grid gap-4 lg:grid-cols-[0.7fr_1.3fr]">
                        <label className="space-y-2 text-sm text-zinc-300">
                            <span>Destination Account</span>
                            <select
                                value={selectedAccountId}
                                onChange={(event) => setSelectedAccountId(event.target.value)}
                                className="w-full rounded-md border border-zinc-700 bg-zinc-950 px-3 py-2 text-white outline-none"
                                disabled={accounts.length === 0}
                            >
                                <option value="">
                                    {accounts.length === 0 ? "Create an account first" : "Choose an account"}
                                </option>
                                {accounts.map((account) => (
                                    <option key={account.id} value={account.id}>
                                        {account.name}
                                    </option>
                                ))}
                            </select>
                        </label>

                        <label className="space-y-2 text-sm text-zinc-300">
                            <span>CSV File</span>
                            <input
                                type="file"
                                accept=".csv,text/csv"
                                onChange={(event) => setCsvFile(event.target.files?.[0] ?? null)}
                                className="w-full rounded-md border border-dashed border-zinc-700 bg-zinc-950 px-3 py-2 text-white outline-none file:mr-4 file:rounded-md file:border-0 file:bg-white/[0.08] file:px-3 file:py-2 file:text-sm file:text-zinc-200"
                            />
                            <p className="text-xs text-zinc-500">
                                {csvFile
                                    ? `Ready to import ${csvFile.name} into ${selectedAccount?.name ?? "the selected account"}.`
                                    : "Attach the provider CSV export you want to import."}
                            </p>
                        </label>
                    </div>

                    <button
                        type="submit"
                        disabled={importing || accounts.length === 0}
                        className="rounded-md bg-amber-300 px-4 py-2.5 text-sm font-semibold text-black disabled:opacity-50"
                    >
                        {importing ? "Importing..." : "Import Transactions"}
                    </button>
                </form>

                {importResult ? (
                    <div className="mt-6 grid gap-6 xl:grid-cols-[0.8fr_1.2fr]">
                        <div className="grid gap-4 sm:grid-cols-2">
                            <SummaryMetric
                                label="Imported"
                                value={`${importResult.importedCount}`}
                                detail="New transactions added to the workspace."
                                tone={importResult.importedCount > 0 ? "success" : "default"}
                            />
                            <SummaryMetric
                                label="Duplicates"
                                value={`${importResult.duplicateCount}`}
                                detail="Rows safely skipped because they already existed."
                            />
                            <SummaryMetric
                                label="Review Required"
                                value={`${importResult.reviewRequiredCount}`}
                                detail="Transactions that need a human category decision."
                                tone={importResult.reviewRequiredCount > 0 ? "warning" : "success"}
                            />
                            <SummaryMetric
                                label="Posted"
                                value={`${importResult.postedCount}`}
                                detail="Transactions that posted cleanly."
                            />
                        </div>

                        <div className="rounded-lg border border-white/10 bg-white/[0.03] p-4">
                            <div className="flex items-center justify-between gap-3">
                                <h3 className="text-sm font-semibold text-white">Imported transaction preview</h3>
                                <div className="flex gap-2">
                                    <Link
                                        href="/review-queue"
                                        className="rounded-md border border-white/10 px-3 py-2 text-xs text-zinc-200 hover:bg-white/[0.05]"
                                    >
                                        Review queue
                                    </Link>
                                    <Link
                                        href="/dashboard"
                                        className="rounded-md border border-white/10 px-3 py-2 text-xs text-zinc-200 hover:bg-white/[0.05]"
                                    >
                                        Dashboard
                                    </Link>
                                </div>
                            </div>

                            {importResult.transactions.length > 0 ? (
                                <div className="mt-4 space-y-3">
                                    {importResult.transactions.slice(0, 5).map((transaction) => (
                                        <div
                                            key={transaction.transactionId}
                                            className="rounded-md border border-white/10 bg-black/20 px-3 py-3"
                                        >
                                            <div className="flex items-center justify-between gap-3">
                                                <p className="text-sm font-medium text-white">
                                                    {transaction.merchant}
                                                </p>
                                                <span className="text-xs text-zinc-400">
                                                    {transaction.status}
                                                </span>
                                            </div>
                                            <p className="mt-1 text-xs text-zinc-400">
                                                {transaction.transactionDate} · ${Number(transaction.amount).toFixed(2)} · {transaction.finalCategory ?? transaction.proposedCategory ?? "UNCATEGORIZED"}
                                            </p>
                                        </div>
                                    ))}
                                </div>
                            ) : (
                                <p className="mt-4 text-sm text-zinc-400">
                                    The import completed without new preview rows, usually because the file was all duplicates.
                                </p>
                            )}
                        </div>
                    </div>
                ) : null}
            </SectionBand>
        </main>
    );
}
