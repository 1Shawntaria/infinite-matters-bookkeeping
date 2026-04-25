"use client";

import { useQuery } from "@tanstack/react-query";
import { useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import {
    getReconciliationDashboard,
    startReconciliation,
    ReconciliationDashboard,
} from "@/lib/api/reconciliation";
import { useOrganizationSession } from "@/lib/auth/session";
import {
    LoadingPanel,
    PageHero,
    SectionBand,
    StatusBanner,
    SummaryMetric,
} from "@/components/app-surfaces";

type BalanceInputs = Record<
    string,
    {
        openingBalance: string;
        statementEndingBalance: string;
    }
>;

export default function ReconciliationPage() {
    const router = useRouter();
    const { organizationId, hydrated } = useOrganizationSession();
    const [error, setError] = useState("");
    const [balanceInputs, setBalanceInputs] = useState<BalanceInputs>({});
    const [startingAccountId, setStartingAccountId] = useState<string | null>(null);
    const reconciliationQuery = useQuery<ReconciliationDashboard, Error>({
        queryKey: ["reconciliationDashboard", organizationId],
        enabled: hydrated && Boolean(organizationId),
        queryFn: async () => {
            const result = await getReconciliationDashboard(organizationId);
            setError("");
            return result;
        },
    });
    const data = organizationId ? (reconciliationQuery.data ?? null) : null;
    const loading = hydrated && organizationId ? reconciliationQuery.isLoading : false;
    const queryError = reconciliationQuery.error?.message ?? "";

    function updateBalanceInput(accountId: string, field: "openingBalance" | "statementEndingBalance", value: string) {
        setBalanceInputs((current) => ({
            ...current,
            [accountId]: {
                openingBalance: current[accountId]?.openingBalance ?? "",
                statementEndingBalance: current[accountId]?.statementEndingBalance ?? "",
                [field]: value,
            },
        }));
    }

    async function handleStartReconciliation(accountId: string, actionPath: string) {
        if (!organizationId || !data?.focusMonth) {
            setError("No organization ID found. Please sign in again.");
            return;
        }

        const inputs = balanceInputs[accountId] ?? {
            openingBalance: "",
            statementEndingBalance: "",
        };
        const parsedOpeningBalance = Number(inputs.openingBalance);
        const parsedStatementEndingBalance = Number(inputs.statementEndingBalance);

        if (!Number.isFinite(parsedOpeningBalance) || !Number.isFinite(parsedStatementEndingBalance)) {
            setError("Enter valid opening and statement ending balances before starting.");
            return;
        }

        setError("");
        setStartingAccountId(accountId);

        try {
            await startReconciliation(organizationId, {
                financialAccountId: accountId,
                month: data.focusMonth,
                openingBalance: parsedOpeningBalance,
                statementEndingBalance: parsedStatementEndingBalance,
            });
            router.push(actionPath);
        } catch (err) {
            const message =
                err instanceof Error ? err.message : "Unable to start reconciliation.";
            setError(message);
        } finally {
            setStartingAccountId(null);
        }
    }

    if (!hydrated || loading) {
        return (
            <LoadingPanel
                title="Loading reconciliation workspace."
                message="Pulling account-level blockers and close readiness into one place."
            />
        );
    }

    if (!organizationId || error || queryError) {
        return (
            <main className="p-4 sm:p-6 lg:p-8">
                <StatusBanner
                    tone="error"
                    title="Reconciliation unavailable"
                    message={error || queryError || "No organization ID found. Please sign in again."}
                />
            </main>
        );
    }

    const unreconciledAccounts = data?.unreconciledAccounts ?? [];

    return (
        <main className="space-y-8 p-4 sm:p-6 lg:p-8">
            <PageHero
                eyebrow="Period close readiness"
                title="Reconciliation"
                description="Manage account-level balance checks and keep close blockers visible before they turn into period-end surprises."
                aside={
                    <SummaryMetric
                        label="Focus Month"
                        value={data?.focusMonth ?? "-"}
                        detail="The current accounting period driving reconciliation work."
                    />
                }
            />

            <div className="grid gap-4 md:grid-cols-3">
                <SummaryMetric
                    label="Focus Month"
                    value={data?.focusMonth ?? "-"}
                    detail="The close period currently under review."
                />
                <SummaryMetric
                    label="Unreconciled Accounts"
                    value={`${data?.period?.unreconciledAccountCount ?? unreconciledAccounts.length}`}
                    detail="Accounts still blocking a clean period close."
                    tone={unreconciledAccounts.length > 0 ? "warning" : "success"}
                />
                <SummaryMetric
                    label="Close Ready"
                    value={data?.period?.closeReady ? "Yes" : "No"}
                    detail={data?.period?.closeReady ? "No reconciliation blockers remain." : "At least one account still needs work."}
                    tone={data?.period?.closeReady ? "success" : "warning"}
                />
            </div>

            <SectionBand
                eyebrow="Period close status"
                title={
                    data?.period?.closeReady
                        ? "This period is ready to close"
                        : "This period is not ready to close"
                }
                description={
                    data?.period?.recommendedActionLabel ??
                    "No recommended action is currently available."
                }
            >
                <div className="flex flex-wrap gap-3 text-sm">
                    {data?.period?.recommendedActionUrgency ? (
                        <span className="rounded-full border border-amber-400/30 bg-amber-300/10 px-3 py-1 text-amber-100">
                            Urgency: {data.period.recommendedActionUrgency}
                        </span>
                    ) : null}
                    {!data?.period?.closeReady && unreconciledAccounts.length > 0 ? (
                        <span className="rounded-full border border-white/10 bg-white/[0.04] px-3 py-1 text-zinc-300">
                            Start or resume from the account cards below.
                        </span>
                    ) : null}
                </div>
            </SectionBand>

            <SectionBand
                eyebrow="Unreconciled accounts"
                title="Accounts needing attention"
                description="Keep statement balances tied to the right account and start work from the specific account that needs attention."
            >
                <div className="mt-4 space-y-4">
                    {unreconciledAccounts.length === 0 ? (
                        <StatusBanner
                            tone="success"
                            title="No unreconciled accounts remaining"
                            message="The close workflow is clear on account-level balance checks for this period."
                        />
                    ) : (
                        unreconciledAccounts.map((account) => (
                            <div
                                key={account.itemId}
                                className="rounded-lg border border-white/10 bg-white/[0.03] p-5 transition hover:border-white/20"
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
                                            className="rounded-md bg-red-500 px-4 py-2 text-center text-sm font-medium text-white hover:bg-red-600"
                                        >
                                            {account.sessionStarted ? "Resume Reconciliation" : "Review Account"}
                                        </Link>

                                        {account.sessionStarted ? (
                                            <p className="text-xs text-zinc-500">
                                                A reconciliation session is already open for this account.
                                            </p>
                                        ) : (
                                            <>
                                                <label className="space-y-2 text-sm text-zinc-300">
                                                    <span>Opening Balance</span>
                                                    <input
                                                        className="w-full rounded-md border border-zinc-700 bg-zinc-950 px-3 py-2 text-white outline-none"
                                                        inputMode="decimal"
                                                        type="number"
                                                        step="0.01"
                                                        value={balanceInputs[account.accountId]?.openingBalance ?? ""}
                                                        onChange={(event) =>
                                                            updateBalanceInput(
                                                                account.accountId,
                                                                "openingBalance",
                                                                event.target.value
                                                            )
                                                        }
                                                    />
                                                </label>

                                                <label className="space-y-2 text-sm text-zinc-300">
                                                    <span>Statement Ending Balance</span>
                                                    <input
                                                        className="w-full rounded-md border border-zinc-700 bg-zinc-950 px-3 py-2 text-white outline-none"
                                                        inputMode="decimal"
                                                        type="number"
                                                        step="0.01"
                                                        value={
                                                            balanceInputs[account.accountId]
                                                                ?.statementEndingBalance ?? ""
                                                        }
                                                        onChange={(event) =>
                                                            updateBalanceInput(
                                                                account.accountId,
                                                                "statementEndingBalance",
                                                                event.target.value
                                                            )
                                                        }
                                                    />
                                                </label>

                                                <button
                                                    onClick={() =>
                                                        handleStartReconciliation(
                                                            account.accountId,
                                                            account.actionPath
                                                        )
                                                    }
                                                    disabled={startingAccountId === account.accountId}
                                                    className="rounded-md border border-zinc-700 px-4 py-2 text-sm font-medium text-white hover:bg-zinc-900 disabled:opacity-50"
                                                >
                                                    {startingAccountId === account.accountId
                                                        ? "Starting..."
                                                        : "Start Reconciliation"}
                                                </button>
                                            </>
                                        )}
                                    </div>
                                </div>
                            </div>
                        ))
                    )}
                </div>
            </SectionBand>

            <SectionBand
                eyebrow="Reconciliation progress"
                title={
                    unreconciledAccounts.length === 0
                        ? "All accounts are reconciled for this period"
                        : `${unreconciledAccounts.length} account(s) still need attention`
                }
                description={
                    unreconciledAccounts.length === 0
                        ? "This period is clear on balance checks and ready for the next close step."
                        : "Work through the accounts above to clear the remaining blockers."
                }
            >
                <div className="grid gap-4 md:grid-cols-2">
                    <SummaryMetric
                        label="Accounts remaining"
                        value={`${unreconciledAccounts.length}`}
                        detail="This count should trend to zero before close."
                        tone={unreconciledAccounts.length === 0 ? "success" : "warning"}
                    />
                    <SummaryMetric
                        label="Next move"
                        value={
                            unreconciledAccounts.length === 0
                                ? "Move to close"
                                : "Start the next account"
                        }
                        detail="The fastest path is always the next account card above."
                    />
                </div>
            </SectionBand>
        </main>
    );
}
