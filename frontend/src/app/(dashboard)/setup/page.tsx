"use client";

import { useQuery, useQueryClient } from "@tanstack/react-query";
import Link from "next/link";
import { useCallback, useEffect, useMemo, useState } from "react";
import {
    createFinancialAccount,
    FinancialAccount,
    listFinancialAccounts,
} from "@/lib/api/accounts";
import {
    importTransactionsCsv,
    ImportBatchResult,
    ImportedTransactionHistoryItem,
    listImportHistory,
} from "@/lib/api/imports";
import { getReviewTasks, ReviewTask } from "@/lib/api/reviews";
import { useOrganizationSession } from "@/lib/auth/session";
import {
    LoadingPanel,
    NextStepsList,
    PageHero,
    ProgressMeter,
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
const EXPECTED_CSV_HEADERS = ["id", "date", "merchant", "memo", "amount", "mcc"] as const;
const SAMPLE_ACCOUNT_NAME = "Demo Operating Checking";
const SAMPLE_ACCOUNT_INSTITUTION = "Infinite Matters Sample Bank";
const SAMPLE_CSV = `id,date,merchant,memo,amount,mcc
sample-20260401,2026-04-01,STARBUCKS,coffee with client,18.45,5814
sample-20260402,2026-04-02,ADOBE,design software,64.99,5734
sample-20260403,2026-04-03,AMZN MKTP,office restock,142.18,5942
sample-20260404,2026-04-04,SHELL,field visit fuel,52.81,5541
sample-20260405,2026-04-05,CLOUDCO,monthly infrastructure,89.00,5734
sample-20260406,2026-04-06,UNKNOWN VENDOR,needs category review,49.99,5734
`;

type CsvPreview = {
    headers: string[];
    sampleRowCount: number;
    hasExpectedHeaders: boolean;
    missingHeaders: string[];
};

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
    const [csvPreview, setCsvPreview] = useState<CsvPreview | null>(null);
    const [creatingAccount, setCreatingAccount] = useState(false);
    const [importing, setImporting] = useState(false);
    const [bootstrappingSample, setBootstrappingSample] = useState(false);
    const [autoSampleTriggered, setAutoSampleTriggered] = useState(false);
    const [showWelcomeState, setShowWelcomeState] = useState(false);
    const [autoDemoRequested, setAutoDemoRequested] = useState(false);

    const accountsQuery = useQuery<FinancialAccount[], Error>({
        queryKey: ["financialAccounts", organizationId],
        enabled: hydrated && Boolean(organizationId),
        queryFn: () => listFinancialAccounts(organizationId),
    });
    const importHistoryQuery = useQuery<ImportedTransactionHistoryItem[], Error>({
        queryKey: ["importHistory", organizationId],
        enabled: hydrated && Boolean(organizationId),
        queryFn: () => listImportHistory(organizationId),
    });
    const reviewTasksQuery = useQuery<ReviewTask[], Error>({
        queryKey: ["reviewTasks", organizationId],
        enabled: hydrated && Boolean(organizationId),
        queryFn: () => getReviewTasks(organizationId),
    });
    const accounts = useMemo(() => accountsQuery.data ?? [], [accountsQuery.data]);
    const importHistory = useMemo(() => importHistoryQuery.data ?? [], [importHistoryQuery.data]);
    const reviewTasks = useMemo(() => reviewTasksQuery.data ?? [], [reviewTasksQuery.data]);
    const loading =
        hydrated && organizationId
            ? accountsQuery.isLoading || importHistoryQuery.isLoading || reviewTasksQuery.isLoading
            : false;
    const queryError =
        accountsQuery.error?.message ??
        importHistoryQuery.error?.message ??
        reviewTasksQuery.error?.message ??
        "";

    useEffect(() => {
        if (!selectedAccountId && accounts[0]?.id) {
            setSelectedAccountId(accounts[0].id);
        }
    }, [accounts, selectedAccountId]);

    const selectedAccount = useMemo(
        () => accounts.find((account) => account.id === selectedAccountId) ?? null,
        [accounts, selectedAccountId]
    );
    const latestImport = importHistory[0] ?? null;
    const latestImportByAccount = useMemo(() => {
        const latestByAccount = new Map<string, ImportedTransactionHistoryItem>();

        for (const item of importHistory) {
            if (!latestByAccount.has(item.financialAccountId)) {
                latestByAccount.set(item.financialAccountId, item);
            }
        }

        return latestByAccount;
    }, [importHistory]);
    const visibleImportHistory = useMemo(
        () =>
            selectedAccountId
                ? importHistory.filter((item) => item.financialAccountId === selectedAccountId)
                : importHistory,
        [importHistory, selectedAccountId]
    );
    const hasImportedActivity = importHistory.length > 0;
    const outstandingReviewTasks = reviewTasks.length;
    const setupCompletionSteps = [
        accounts.length > 0,
        hasImportedActivity,
        hasImportedActivity && outstandingReviewTasks === 0,
    ];
    const completedSteps = setupCompletionSteps.filter(Boolean).length;

    const refreshWorkspaceQueries = useCallback(async (activeOrganizationId: string) => {
        await queryClient.invalidateQueries({ queryKey: ["financialAccounts", activeOrganizationId] });
        await queryClient.invalidateQueries({ queryKey: ["dashboardSnapshot", activeOrganizationId] });
        await queryClient.invalidateQueries({ queryKey: ["reviewTasks", activeOrganizationId] });
        await queryClient.invalidateQueries({ queryKey: ["reconciliationDashboard", activeOrganizationId] });
        await queryClient.invalidateQueries({ queryKey: ["importHistory", activeOrganizationId] });
    }, [queryClient]);

    const runImport = useCallback(async (
        activeOrganizationId: string,
        financialAccountId: string,
        file: File
    ) => {
        const result = await importTransactionsCsv(activeOrganizationId, financialAccountId, file);
        setImportResult(result);
        setCsvFile(null);
        setCsvPreview(null);
        await refreshWorkspaceQueries(activeOrganizationId);
        return result;
    }, [refreshWorkspaceQueries]);

    async function inspectCsvFile(file: File | null) {
        if (!file) {
            setCsvPreview(null);
            return;
        }

        const fileText = await file.text();
        const lines = fileText
            .split(/\r?\n/)
            .map((line) => line.trim())
            .filter(Boolean);

        if (lines.length === 0) {
            setCsvPreview({
                headers: [],
                sampleRowCount: 0,
                hasExpectedHeaders: false,
                missingHeaders: [...EXPECTED_CSV_HEADERS],
            });
            return;
        }

        const headers = lines[0].split(",").map((header) => header.trim().toLowerCase());
        const missingHeaders = EXPECTED_CSV_HEADERS.filter((header) => !headers.includes(header));

        setCsvPreview({
            headers,
            sampleRowCount: Math.max(0, lines.length - 1),
            hasExpectedHeaders: missingHeaders.length === 0,
            missingHeaders,
        });
    }

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
        if (csvPreview && !csvPreview.hasExpectedHeaders) {
            setImportError(
                `CSV headers are incomplete. Missing: ${csvPreview.missingHeaders.join(", ")}.`
            );
            return;
        }

        setImporting(true);
        setImportError("");
        setImportSuccess("");

        try {
            await runImport(organizationId, selectedAccountId, csvFile);
            setImportSuccess("Import completed successfully.");
        } catch (err) {
            setImportResult(null);
            setImportError(err instanceof Error ? err.message : "Unable to import CSV.");
        } finally {
            setImporting(false);
        }
    }

    const handleLoadSampleWorkspace = useCallback(async () => {
        if (!organizationId) {
            setImportError("No organization ID found. Please sign in again.");
            return;
        }

        setBootstrappingSample(true);
        setAccountError("");
        setAccountSuccess("");
        setImportError("");
        setImportSuccess("");

        try {
            const existingUnusedDemoAccount = accounts.find(
                (account) =>
                    account.name === SAMPLE_ACCOUNT_NAME &&
                    !latestImportByAccount.get(account.id)
            );

            const sampleAccount =
                existingUnusedDemoAccount ??
                (await createFinancialAccount({
                    organizationId,
                    name: existingUnusedDemoAccount
                        ? SAMPLE_ACCOUNT_NAME
                        : accounts.some((account) => account.name === SAMPLE_ACCOUNT_NAME)
                          ? `${SAMPLE_ACCOUNT_NAME} ${accounts.length + 1}`
                          : SAMPLE_ACCOUNT_NAME,
                    accountType: "BANK",
                    institutionName: SAMPLE_ACCOUNT_INSTITUTION,
                    currency: "USD",
                }));

            setSelectedAccountId(sampleAccount.id);
            const sampleFile = new File([SAMPLE_CSV], "infinite-matters-sample.csv", {
                type: "text/csv",
            });
            await runImport(organizationId, sampleAccount.id, sampleFile);
            setAccountSuccess(`${sampleAccount.name} is ready for imports.`);
            setImportSuccess("Sample workspace loaded successfully.");
        } catch (err) {
            setImportResult(null);
            setImportError(
                err instanceof Error ? err.message : "Unable to load sample workspace."
            );
        } finally {
            setBootstrappingSample(false);
        }
    }, [accounts, latestImportByAccount, organizationId, runImport]);

    useEffect(() => {
        if (typeof window === "undefined") {
            return;
        }

        const params = new URLSearchParams(window.location.search);
        setShowWelcomeState(params.get("welcome") === "1" || params.get("demo") === "1");
        setAutoDemoRequested(params.get("demo") === "1");
    }, []);

    useEffect(() => {
        if (
            !autoDemoRequested ||
            autoSampleTriggered ||
            !hydrated ||
            !organizationId ||
            bootstrappingSample
        ) {
            return;
        }

        setAutoSampleTriggered(true);
        void handleLoadSampleWorkspace();
    }, [
        autoDemoRequested,
        autoSampleTriggered,
        bootstrappingSample,
        handleLoadSampleWorkspace,
        hydrated,
        organizationId,
    ]);

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
                                latestImport
                                    ? new Date(latestImport.importedAt).toLocaleDateString("en-US", {
                                          month: "short",
                                          day: "numeric",
                                      })
                                    : "Not started"
                            }
                            detail={
                                latestImport
                                    ? `${latestImport.financialAccountName} · ${latestImport.merchant}`
                                    : "Successful imports immediately update the rest of the workspace."
                            }
                            tone={latestImport ? "success" : "default"}
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
                {showWelcomeState ? (
                    <div className="mb-5 grid gap-4 lg:grid-cols-[1fr_1fr]">
                        <div className="rounded-lg border border-emerald-400/30 bg-emerald-300/10 p-4">
                            <p className="text-xs font-medium uppercase tracking-[0.18em] text-emerald-200">
                                Welcome aboard
                            </p>
                            <h3 className="mt-2 text-lg font-semibold text-white">
                                You are in the guided setup lane
                            </h3>
                            <p className="mt-2 text-sm text-zinc-300">
                                Start with sample data for a fast product tour, or bring your own
                                CSV when you want to validate the real onboarding path.
                            </p>
                        </div>
                        <div className="rounded-lg border border-white/10 bg-black/20 p-4">
                            <p className="text-sm font-semibold text-white">What this unlocks</p>
                            <div className="mt-3 space-y-2 text-sm text-zinc-400">
                                <p>1. Dashboard metrics become meaningful instead of empty.</p>
                                <p>2. Review queue items appear when merchant decisions are needed.</p>
                                <p>3. Reconciliation has real account activity to work from.</p>
                            </div>
                        </div>
                    </div>
                ) : null}
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
                <div className="mt-5 grid gap-4 lg:grid-cols-[0.95fr_1.05fr]">
                    <div className="rounded-lg border border-white/10 bg-black/20 p-4">
                        <p className="text-xs font-medium uppercase tracking-[0.18em] text-zinc-500">
                            Fastest sandbox path
                        </p>
                        <h3 className="mt-2 text-lg font-semibold text-white">
                            Load sample activity without leaving the app
                        </h3>
                        <p className="mt-2 text-sm text-zinc-400">
                            This creates a demo checking account and imports a realistic CSV so the
                            dashboard, review queue, and reconciliation views all have something to
                            work with immediately.
                        </p>
                        <button
                            type="button"
                            onClick={handleLoadSampleWorkspace}
                            disabled={bootstrappingSample || importing}
                            className="mt-4 rounded-md bg-emerald-300 px-4 py-2.5 text-sm font-semibold text-black disabled:opacity-50"
                        >
                            {bootstrappingSample ? "Loading sample workspace..." : "Load sample workspace"}
                        </button>
                    </div>
                    <StatusBanner
                        tone="muted"
                        title="Two equally valid ways to start"
                        message="Use sample data when you want to explore the product shape quickly. Use your own CSV when you are ready to validate the real import path."
                    />
                </div>
                <div className="mt-5 rounded-lg border border-white/10 bg-white/[0.03] p-4">
                    <ProgressMeter
                        label="Setup progress"
                        value={completedSteps}
                        total={3}
                        tone={completedSteps === 3 ? "success" : "warning"}
                    />
                    <div className="mt-4 grid gap-3 md:grid-cols-3">
                        <div className="rounded-md border border-white/10 bg-black/20 px-3 py-3">
                            <p className="text-xs uppercase tracking-[0.18em] text-zinc-500">1. Account</p>
                            <p className="mt-2 text-sm font-medium text-white">
                                {accounts.length > 0 ? "Ready" : "Still needed"}
                            </p>
                        </div>
                        <div className="rounded-md border border-white/10 bg-black/20 px-3 py-3">
                            <p className="text-xs uppercase tracking-[0.18em] text-zinc-500">2. Import</p>
                            <p className="mt-2 text-sm font-medium text-white">
                                {hasImportedActivity ? "Completed" : "Still needed"}
                            </p>
                        </div>
                        <div className="rounded-md border border-white/10 bg-black/20 px-3 py-3">
                            <p className="text-xs uppercase tracking-[0.18em] text-zinc-500">3. Review follow-up</p>
                            <p className="mt-2 text-sm font-medium text-white">
                                {hasImportedActivity
                                    ? outstandingReviewTasks > 0
                                        ? `${outstandingReviewTasks} item(s) next`
                                        : "Clear"
                                    : "Pending import"}
                            </p>
                        </div>
                    </div>
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
                                            <p className="mt-2 text-xs text-zinc-500">
                                                {latestImportByAccount.get(account.id)
                                                    ? `Last import ${new Date(latestImportByAccount.get(account.id)!.importedAt).toLocaleDateString("en-US", {
                                                          month: "short",
                                                          day: "numeric",
                                                      })} · ${latestImportByAccount.get(account.id)!.merchant}`
                                                    : "No imported transactions yet."}
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
                                onChange={async (event) => {
                                    const file = event.target.files?.[0] ?? null;
                                    setCsvFile(file);
                                    setImportError("");
                                    await inspectCsvFile(file);
                                }}
                                className="w-full rounded-md border border-dashed border-zinc-700 bg-zinc-950 px-3 py-2 text-white outline-none file:mr-4 file:rounded-md file:border-0 file:bg-white/[0.08] file:px-3 file:py-2 file:text-sm file:text-zinc-200"
                            />
                            <p className="text-xs text-zinc-500">
                                {csvFile
                                    ? `Ready to import ${csvFile.name} into ${selectedAccount?.name ?? "the selected account"}.`
                                    : "Attach the provider CSV export you want to import."}
                            </p>
                        </label>
                    </div>

                    <div className="grid gap-4 lg:grid-cols-[0.9fr_1.1fr]">
                        <StatusBanner
                            tone="muted"
                            title="Before you import"
                            message="Use the exported CSV from the financial institution when possible, keep headers intact, and avoid editing row IDs so duplicate detection can protect you."
                        />
                        <div className="rounded-lg border border-white/10 bg-white/[0.03] p-4">
                            <h3 className="text-sm font-semibold text-white">What happens next</h3>
                            <div className="mt-3 space-y-2 text-sm text-zinc-400">
                                <p>1. Clean rows post directly into the workspace.</p>
                                <p>2. Ambiguous merchants become review-queue items.</p>
                                <p>3. Reconciliation picks up the new account activity automatically.</p>
                            </div>
                        </div>
                    </div>

                    {csvFile ? (
                        <div className="grid gap-4 lg:grid-cols-[0.95fr_1.05fr]">
                            <StatusBanner
                                tone={csvPreview?.hasExpectedHeaders ? "success" : "error"}
                                title={
                                    csvPreview?.hasExpectedHeaders
                                        ? "CSV shape looks ready"
                                        : "CSV headers need attention"
                                }
                                message={
                                    csvPreview?.hasExpectedHeaders
                                        ? `Detected ${csvPreview.sampleRowCount} transaction row(s) with the expected import headers.`
                                        : `Expected headers: ${EXPECTED_CSV_HEADERS.join(", ")}. Missing: ${
                                              csvPreview?.missingHeaders.join(", ") || "unknown"
                                          }.`
                                }
                            />
                            <div className="rounded-lg border border-white/10 bg-white/[0.03] p-4">
                                <h3 className="text-sm font-semibold text-white">Pre-submit check</h3>
                                <div className="mt-3 space-y-2 text-sm text-zinc-400">
                                    <p>File: {csvFile.name}</p>
                                    <p>
                                        Headers:{" "}
                                        {csvPreview?.headers.length
                                            ? csvPreview.headers.join(", ")
                                            : "Not detected"}
                                    </p>
                                    <p>
                                        Recommendation:{" "}
                                        {csvPreview?.hasExpectedHeaders
                                            ? "Safe to import with duplicate protection."
                                            : "Re-export from the provider before importing."}
                                    </p>
                                </div>
                            </div>
                        </div>
                    ) : null}

                    <button
                        type="submit"
                        disabled={
                            importing ||
                            accounts.length === 0 ||
                            (csvFile != null && csvPreview != null && !csvPreview.hasExpectedHeaders)
                        }
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

                {importResult ? (
                    <div className="mt-6 grid gap-4 lg:grid-cols-[1.1fr_0.9fr]">
                        <NextStepsList
                            title="Recommended follow-up"
                            items={
                                importResult.reviewRequiredCount > 0
                                    ? [
                                          "Open the review queue and resolve the imported ambiguous merchants first.",
                                          "Return to the dashboard to confirm workflow pressure and close-readiness updated.",
                                          "Move into reconciliation once statement balances are available for the imported account.",
                                      ]
                                    : [
                                          "Return to the dashboard to confirm the new activity appears in the workspace pulse.",
                                          "Open reconciliation if this account is part of the current close cycle.",
                                          "Import the next account or statement while momentum is high.",
                                      ]
                            }
                        />
                        <StatusBanner
                            tone={importResult.reviewRequiredCount > 0 ? "error" : "success"}
                            title={
                                importResult.reviewRequiredCount > 0
                                    ? "Review queue follow-up needed"
                                    : "Import landed cleanly"
                            }
                            message={
                                importResult.reviewRequiredCount > 0
                                    ? `${importResult.reviewRequiredCount} imported transaction(s) still need a category decision before the books are fully clean.`
                                    : `${importResult.postedCount} transaction(s) posted without needing manual review.`
                            }
                        />
                    </div>
                ) : null}
            </SectionBand>

            <SectionBand
                eyebrow="Import traceability"
                title={selectedAccount ? `${selectedAccount.name} activity` : "Recent import activity"}
                description="Use this view to confirm where recent imports landed, when they arrived, and what sort of follow-up they created."
            >
                <div className="grid gap-6 xl:grid-cols-[0.85fr_1.15fr]">
                    <div className="grid gap-4 sm:grid-cols-2">
                        <SummaryMetric
                            label="Tracked imports"
                            value={`${importHistory.length}`}
                            detail="Persisted imported transactions in this workspace."
                            tone={importHistory.length > 0 ? "success" : "default"}
                        />
                        <SummaryMetric
                            label="Selected account"
                            value={`${visibleImportHistory.length}`}
                            detail={
                                selectedAccount
                                    ? `Imported transactions currently tied to ${selectedAccount.name}.`
                                    : "Choose an account to see account-specific import history."
                            }
                            tone={visibleImportHistory.length > 0 ? "success" : "default"}
                        />
                    </div>

                    <div className="rounded-lg border border-white/10 bg-white/[0.03] p-4">
                        <h3 className="text-sm font-semibold text-white">Latest imported rows</h3>
                        <div className="mt-4 space-y-3">
                            {visibleImportHistory.slice(0, 5).map((item) => (
                                <div
                                    key={item.transactionId}
                                    className="rounded-md border border-white/10 bg-black/20 px-3 py-3"
                                >
                                    <div className="flex flex-wrap items-center justify-between gap-3">
                                        <div>
                                            <p className="text-sm font-medium text-white">{item.merchant}</p>
                                            <p className="mt-1 text-xs text-zinc-400">
                                                {item.financialAccountName} · {item.transactionDate} ·{" "}
                                                {new Date(item.importedAt).toLocaleString("en-US", {
                                                    month: "short",
                                                    day: "numeric",
                                                    hour: "numeric",
                                                    minute: "2-digit",
                                                })}
                                            </p>
                                        </div>
                                        <div className="text-right">
                                            <p className="text-sm font-medium text-white">
                                                ${item.amount.toFixed(2)}
                                            </p>
                                            <p className="mt-1 text-xs text-zinc-400">
                                                {item.status} · {item.route}
                                            </p>
                                        </div>
                                    </div>
                                </div>
                            ))}
                            {visibleImportHistory.length === 0 ? (
                                <StatusBanner
                                    tone="muted"
                                    title="No import history yet"
                                    message="Once a CSV lands, this section will show where it went, when it arrived, and what kind of follow-up it created."
                                />
                            ) : null}
                        </div>
                    </div>
                </div>
            </SectionBand>
        </main>
    );
}
