"use client";

import { useQuery, useQueryClient } from "@tanstack/react-query";
import { useEffect, useState } from "react";
import {
    closePeriod,
    CloseChecklistSummary,
    createAdjustmentEntry,
    forceClosePeriod,
    getCloseChecklist,
    LedgerEntrySummary,
    listAccountingPeriods,
    listLedgerEntries,
    AccountingPeriodSummary,
} from "@/lib/api/close";
import { getDashboardSnapshot, DashboardSnapshot } from "@/lib/api/dashboard";
import { useOrganizationSession } from "@/lib/auth/session";
import {
    LoadingPanel,
    NextStepsList,
    PageHero,
    SectionBand,
    StatusBanner,
    SummaryMetric,
} from "@/components/app-surfaces";

type AdjustmentLineDraft = {
    accountCode: string;
    accountName: string;
    entrySide: "DEBIT" | "CREDIT";
    amount: string;
};

const EMPTY_LINE = (): AdjustmentLineDraft => ({
    accountCode: "",
    accountName: "",
    entrySide: "DEBIT",
    amount: "",
});

export default function ClosePage() {
    const queryClient = useQueryClient();
    const { organizationId, hydrated } = useOrganizationSession();
    const [selectedMonth, setSelectedMonth] = useState("");
    const [closeError, setCloseError] = useState("");
    const [closeSuccess, setCloseSuccess] = useState("");
    const [forceCloseReason, setForceCloseReason] = useState("");
    const [adjustmentError, setAdjustmentError] = useState("");
    const [adjustmentSuccess, setAdjustmentSuccess] = useState("");
    const [postingAdjustment, setPostingAdjustment] = useState(false);
    const [closingPeriod, setClosingPeriod] = useState(false);
    const [forceClosingPeriod, setForceClosingPeriod] = useState(false);
    const [entryDate, setEntryDate] = useState("");
    const [description, setDescription] = useState("");
    const [adjustmentReason, setAdjustmentReason] = useState("");
    const [lines, setLines] = useState<AdjustmentLineDraft[]>([EMPTY_LINE(), EMPTY_LINE()]);

    const dashboardQuery = useQuery<DashboardSnapshot, Error>({
        queryKey: ["dashboardSnapshot", organizationId],
        enabled: hydrated && Boolean(organizationId),
        queryFn: () => getDashboardSnapshot(organizationId),
    });
    const periodsQuery = useQuery<AccountingPeriodSummary[], Error>({
        queryKey: ["accountingPeriods", organizationId],
        enabled: hydrated && Boolean(organizationId),
        queryFn: () => listAccountingPeriods(organizationId),
    });
    const checklistQuery = useQuery<CloseChecklistSummary, Error>({
        queryKey: ["closeChecklist", organizationId, selectedMonth],
        enabled: hydrated && Boolean(organizationId) && Boolean(selectedMonth),
        queryFn: () => getCloseChecklist(organizationId, selectedMonth),
    });
    const ledgerQuery = useQuery<LedgerEntrySummary[], Error>({
        queryKey: ["ledgerEntries", organizationId],
        enabled: hydrated && Boolean(organizationId),
        queryFn: () => listLedgerEntries(organizationId),
    });

    useEffect(() => {
        if (!selectedMonth && dashboardQuery.data?.focusMonth) {
            setSelectedMonth(dashboardQuery.data.focusMonth);
        }
    }, [dashboardQuery.data?.focusMonth, selectedMonth]);

    useEffect(() => {
        if (!entryDate && selectedMonth) {
            setEntryDate(`${selectedMonth}-01`);
        }
    }, [entryDate, selectedMonth]);

    const loading =
        hydrated && organizationId
            ? dashboardQuery.isLoading ||
              periodsQuery.isLoading ||
              ledgerQuery.isLoading ||
              (Boolean(selectedMonth) && checklistQuery.isLoading)
            : false;
    const queryError =
        dashboardQuery.error?.message ??
        periodsQuery.error?.message ??
        checklistQuery.error?.message ??
        ledgerQuery.error?.message ??
        "";

    const periods = periodsQuery.data ?? [];
    const checklist = checklistQuery.data ?? null;
    const ledgerEntries = ledgerQuery.data ?? [];
    const currentPeriod = periods.find(
        (period) => period.periodStart.slice(0, 7) === selectedMonth
    ) ?? null;
    const completeChecklistItems =
        checklist?.items.filter((item) => item.complete).length ?? 0;
    const totalChecklistItems = checklist?.items.length ?? 0;
    const ledgerPreview = ledgerEntries.slice(0, 8);
    const totalDebits = lines.reduce(
        (sum, line) =>
            sum +
            (line.entrySide === "DEBIT" ? Number.parseFloat(line.amount || "0") || 0 : 0),
        0
    );
    const totalCredits = lines.reduce(
        (sum, line) =>
            sum +
            (line.entrySide === "CREDIT" ? Number.parseFloat(line.amount || "0") || 0 : 0),
        0
    );
    const adjustmentsBalanced = totalDebits > 0 && totalDebits === totalCredits;

    function updateLine(index: number, field: keyof AdjustmentLineDraft, value: string) {
        setLines((current) =>
            current.map((line, lineIndex) =>
                lineIndex === index ? { ...line, [field]: value } : line
            )
        );
    }

    function addLine() {
        setLines((current) => [...current, EMPTY_LINE()]);
    }

    function removeLine(index: number) {
        setLines((current) =>
            current.length <= 2 ? current : current.filter((_, lineIndex) => lineIndex !== index)
        );
    }

    async function refreshCloseQueries(activeOrganizationId: string) {
        await queryClient.invalidateQueries({ queryKey: ["dashboardSnapshot", activeOrganizationId] });
        await queryClient.invalidateQueries({ queryKey: ["accountingPeriods", activeOrganizationId] });
        await queryClient.invalidateQueries({
            queryKey: ["closeChecklist", activeOrganizationId, selectedMonth],
        });
        await queryClient.invalidateQueries({ queryKey: ["ledgerEntries", activeOrganizationId] });
        await queryClient.invalidateQueries({ queryKey: ["reconciliationDashboard", activeOrganizationId] });
    }

    async function handleClosePeriod() {
        if (!organizationId || !selectedMonth) return;
        setCloseError("");
        setCloseSuccess("");
        setClosingPeriod(true);

        try {
            const result = await closePeriod(organizationId, selectedMonth);
            await refreshCloseQueries(organizationId);
            setCloseSuccess(
                `Period ${selectedMonth} closed successfully with ${result.closeMethod?.toLowerCase() ?? "standard"} controls.`
            );
        } catch (err) {
            setCloseError(err instanceof Error ? err.message : "Unable to close period.");
        } finally {
            setClosingPeriod(false);
        }
    }

    async function handleForceClosePeriod() {
        if (!organizationId || !selectedMonth) return;
        if (!forceCloseReason.trim()) {
            setCloseError("Provide an override reason before force closing a period.");
            return;
        }

        setCloseError("");
        setCloseSuccess("");
        setForceClosingPeriod(true);

        try {
            await forceClosePeriod(organizationId, selectedMonth, forceCloseReason.trim());
            await refreshCloseQueries(organizationId);
            setCloseSuccess(`Period ${selectedMonth} was force-closed with an override reason.`);
            setForceCloseReason("");
        } catch (err) {
            setCloseError(err instanceof Error ? err.message : "Unable to force close period.");
        } finally {
            setForceClosingPeriod(false);
        }
    }

    async function handlePostAdjustment(event: React.FormEvent<HTMLFormElement>) {
        event.preventDefault();
        if (!organizationId) {
            setAdjustmentError("No organization ID found. Please sign in again.");
            return;
        }
        if (!adjustmentsBalanced) {
            setAdjustmentError("Adjustments must balance total debits and credits before posting.");
            return;
        }

        const payloadLines = lines.map((line) => ({
            accountCode: line.accountCode.trim(),
            accountName: line.accountName.trim(),
            entrySide: line.entrySide,
            amount: Number.parseFloat(line.amount),
        }));

        if (
            payloadLines.some(
                (line) =>
                    !line.accountCode ||
                    !line.accountName ||
                    !Number.isFinite(line.amount) ||
                    line.amount <= 0
            )
        ) {
            setAdjustmentError("Every adjustment line needs an account code, name, side, and amount.");
            return;
        }

        setPostingAdjustment(true);
        setAdjustmentError("");
        setAdjustmentSuccess("");

        try {
            await createAdjustmentEntry(organizationId, {
                entryDate,
                description: description.trim(),
                adjustmentReason: adjustmentReason.trim(),
                lines: payloadLines,
            });
            await refreshCloseQueries(organizationId);
            setAdjustmentSuccess("Adjustment posted successfully and reflected in the ledger.");
            setDescription("");
            setAdjustmentReason("");
            setLines([EMPTY_LINE(), EMPTY_LINE()]);
        } catch (err) {
            setAdjustmentError(
                err instanceof Error ? err.message : "Unable to post adjustment entry."
            );
        } finally {
            setPostingAdjustment(false);
        }
    }

    if (!hydrated || loading) {
        return (
            <LoadingPanel
                title="Loading close workspace."
                message="Gathering checklist items, ledger activity, and period controls into one place."
            />
        );
    }

    if (!organizationId || queryError) {
        return (
            <main className="p-4 sm:p-6 lg:p-8">
                <StatusBanner
                    tone="error"
                    title="Close workspace unavailable"
                    message={queryError || "No organization ID found. Please sign in again."}
                />
            </main>
        );
    }

    return (
        <main className="space-y-8 p-4 sm:p-6 lg:p-8">
            <PageHero
                eyebrow="Month-end control center"
                title="Close Management"
                description="Run the month-end checklist, inspect the ledger, post adjusting entries, and close the period without leaving the workspace."
                aside={
                    <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-1">
                        <SummaryMetric
                            label="Focus month"
                            value={selectedMonth || dashboardQuery.data?.focusMonth || "-"}
                            detail="This is the month currently under close review."
                        />
                        <SummaryMetric
                            label="Checklist status"
                            value={checklist?.closeReady ? "Ready" : "In progress"}
                            detail={
                                checklist?.closeReady
                                    ? "All close controls are currently satisfied."
                                    : "At least one control still needs attention."
                            }
                            tone={checklist?.closeReady ? "success" : "warning"}
                        />
                    </div>
                }
            >
                <div className="flex flex-wrap items-center gap-3">
                    <label className="text-sm text-zinc-300">
                        <span className="mr-2">Month</span>
                        <input
                            type="month"
                            value={selectedMonth}
                            onChange={(event) => setSelectedMonth(event.target.value)}
                            className="rounded-md border border-zinc-700 bg-zinc-950 px-3 py-2 text-white outline-none"
                        />
                    </label>
                </div>
            </PageHero>

            <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
                <SummaryMetric
                    label="Checklist items"
                    value={`${completeChecklistItems}/${totalChecklistItems || 0}`}
                    detail="Completion across reconciliation, workflow, and close controls."
                    tone={checklist?.closeReady ? "success" : "warning"}
                />
                <SummaryMetric
                    label="Period status"
                    value={currentPeriod?.status ?? "OPEN"}
                    detail={
                        currentPeriod?.closedAt
                            ? `Closed ${new Date(currentPeriod.closedAt).toLocaleDateString("en-US")}.`
                            : "Still open for month-end work."
                    }
                    tone={currentPeriod?.status === "CLOSED" ? "success" : "default"}
                />
                <SummaryMetric
                    label="Ledger entries"
                    value={`${ledgerEntries.length}`}
                    detail="Use recent journal activity to confirm the period tells a coherent story."
                />
                <SummaryMetric
                    label="Unreconciled accounts"
                    value={`${dashboardQuery.data?.period?.unreconciledAccountCount ?? 0}`}
                    detail="Any remaining reconciliations will keep the close from going green."
                    tone={
                        (dashboardQuery.data?.period?.unreconciledAccountCount ?? 0) > 0
                            ? "warning"
                            : "success"
                    }
                />
            </div>

            <SectionBand
                eyebrow="Checklist"
                title="Close readiness checklist"
                description="This mirrors the control logic behind the backend close gate so users can see exactly what still blocks the month."
            >
                <div className="grid gap-4 lg:grid-cols-[1.1fr_0.9fr]">
                    <div className="space-y-3">
                        {(checklist?.items ?? []).map((item) => (
                            <div
                                key={`${item.itemType}-${item.label}`}
                                className="rounded-lg border border-white/10 bg-white/[0.03] p-4"
                            >
                                <div className="flex flex-wrap items-center justify-between gap-3">
                                    <div>
                                        <p className="text-sm font-semibold text-white">{item.label}</p>
                                        <p className="mt-1 text-sm text-zinc-400">{item.detail}</p>
                                    </div>
                                    <span
                                        className={[
                                            "rounded-full px-3 py-1 text-xs font-medium",
                                            item.complete
                                                ? "border border-emerald-400/30 bg-emerald-300/10 text-emerald-100"
                                                : "border border-amber-400/30 bg-amber-300/10 text-amber-100",
                                        ].join(" ")}
                                    >
                                        {item.complete ? "Complete" : "Needs attention"}
                                    </span>
                                </div>
                            </div>
                        ))}
                    </div>
                    <NextStepsList
                        title="How teams usually move this forward"
                        items={[
                            "Clear the review queue first so reconciliation uses final categories instead of provisional ones.",
                            "Resolve remaining account reconciliations before attempting a standard close.",
                            "Post adjusting entries when the ledger needs accruals, corrections, or period-end true-ups.",
                        ]}
                    />
                </div>
            </SectionBand>

            <div className="grid gap-6 xl:grid-cols-[0.95fr_1.05fr]">
                <SectionBand
                    eyebrow="Period actions"
                    title="Close or force-close the month"
                    description="Use a standard close when the checklist is clear. Use force-close only when leadership intentionally accepts an override."
                >
                    {closeError ? (
                        <div className="mb-4">
                            <StatusBanner tone="error" title="Close action failed" message={closeError} />
                        </div>
                    ) : null}
                    {closeSuccess ? (
                        <div className="mb-4">
                            <StatusBanner tone="success" title="Close action completed" message={closeSuccess} />
                        </div>
                    ) : null}

                    <div className="space-y-4">
                        <div className="rounded-lg border border-white/10 bg-black/20 p-4">
                            <p className="text-sm font-semibold text-white">Standard close</p>
                            <p className="mt-2 text-sm text-zinc-400">
                                This path honors the checklist and keeps close discipline visible to the rest of the team.
                            </p>
                            <button
                                type="button"
                                onClick={handleClosePeriod}
                                disabled={closingPeriod || forceClosingPeriod || !selectedMonth}
                                className="mt-4 rounded-md bg-emerald-300 px-4 py-2.5 text-sm font-semibold text-black disabled:opacity-50"
                            >
                                {closingPeriod ? "Closing period..." : "Close period"}
                            </button>
                        </div>

                        <div className="rounded-lg border border-white/10 bg-black/20 p-4">
                            <p className="text-sm font-semibold text-white">Force close with override</p>
                            <p className="mt-2 text-sm text-zinc-400">
                                Reserve this for leadership-approved exceptions, and document the reason clearly.
                            </p>
                            <label className="mt-4 block space-y-2 text-sm text-zinc-300">
                                <span>Override reason</span>
                                <textarea
                                    value={forceCloseReason}
                                    onChange={(event) => setForceCloseReason(event.target.value)}
                                    rows={4}
                                    className="w-full rounded-md border border-zinc-700 bg-zinc-950 px-3 py-2 text-white outline-none"
                                    placeholder="Explain why the period is being force-closed."
                                />
                            </label>
                            <button
                                type="button"
                                onClick={handleForceClosePeriod}
                                disabled={forceClosingPeriod || closingPeriod || !selectedMonth}
                                className="mt-4 rounded-md border border-amber-300/40 bg-amber-300/10 px-4 py-2.5 text-sm font-semibold text-amber-100 disabled:opacity-50"
                            >
                                {forceClosingPeriod ? "Force-closing..." : "Force close period"}
                            </button>
                        </div>
                    </div>
                </SectionBand>

                <SectionBand
                    eyebrow="Period history"
                    title="Recent accounting periods"
                    description="Use this history to confirm what is still open, what was checklist-driven, and what required override handling."
                >
                    <div className="space-y-3">
                        {periods.slice(0, 6).map((period) => {
                            const month = period.periodStart.slice(0, 7);
                            return (
                                <button
                                    key={period.id}
                                    type="button"
                                    onClick={() => setSelectedMonth(month)}
                                    className={[
                                        "w-full rounded-lg border px-4 py-4 text-left transition",
                                        selectedMonth === month
                                            ? "border-emerald-400/40 bg-emerald-300/10"
                                            : "border-white/10 bg-white/[0.03] hover:border-white/20",
                                    ].join(" ")}
                                >
                                    <div className="flex flex-wrap items-center justify-between gap-3">
                                        <div>
                                            <p className="text-sm font-semibold text-white">{month}</p>
                                            <p className="mt-1 text-sm text-zinc-400">
                                                {period.status === "CLOSED"
                                                    ? `Closed via ${period.closeMethod?.toLowerCase() ?? "unknown"} controls.`
                                                    : "Open and available for close work."}
                                            </p>
                                        </div>
                                        <span className="rounded-full border border-white/10 bg-white/[0.04] px-3 py-1 text-xs text-zinc-200">
                                            {period.status}
                                        </span>
                                    </div>
                                </button>
                            );
                        })}
                    </div>
                </SectionBand>
            </div>

            <div className="grid gap-6 xl:grid-cols-[1.02fr_0.98fr]">
                <SectionBand
                    eyebrow="Adjustments"
                    title="Post a manual adjustment"
                    description="Use this when accruals, reclasses, or clean-up entries are needed to get the period truly ready."
                >
                    {adjustmentError ? (
                        <div className="mb-4">
                            <StatusBanner tone="error" title="Adjustment failed" message={adjustmentError} />
                        </div>
                    ) : null}
                    {adjustmentSuccess ? (
                        <div className="mb-4">
                            <StatusBanner tone="success" title="Adjustment posted" message={adjustmentSuccess} />
                        </div>
                    ) : null}

                    <form className="space-y-4" onSubmit={handlePostAdjustment}>
                        <div className="grid gap-4 md:grid-cols-2">
                            <label className="space-y-2 text-sm text-zinc-300">
                                <span>Entry date</span>
                                <input
                                    type="date"
                                    value={entryDate}
                                    onChange={(event) => setEntryDate(event.target.value)}
                                    className="w-full rounded-md border border-zinc-700 bg-zinc-950 px-3 py-2 text-white outline-none"
                                    required
                                />
                            </label>
                            <label className="space-y-2 text-sm text-zinc-300">
                                <span>Description</span>
                                <input
                                    value={description}
                                    onChange={(event) => setDescription(event.target.value)}
                                    className="w-full rounded-md border border-zinc-700 bg-zinc-950 px-3 py-2 text-white outline-none"
                                    placeholder="Accrue April software invoice"
                                    required
                                />
                            </label>
                        </div>

                        <label className="space-y-2 text-sm text-zinc-300">
                            <span>Adjustment reason</span>
                            <textarea
                                value={adjustmentReason}
                                onChange={(event) => setAdjustmentReason(event.target.value)}
                                rows={3}
                                className="w-full rounded-md border border-zinc-700 bg-zinc-950 px-3 py-2 text-white outline-none"
                                placeholder="Document why this entry is necessary for month-end accuracy."
                                required
                            />
                        </label>

                        <div className="space-y-3">
                            {lines.map((line, index) => (
                                <div
                                    key={`${index}-${line.entrySide}`}
                                    className="grid gap-3 rounded-lg border border-white/10 bg-black/20 p-4 md:grid-cols-[0.9fr_1.2fr_0.7fr_0.7fr_auto]"
                                >
                                    <input
                                        value={line.accountCode}
                                        onChange={(event) => updateLine(index, "accountCode", event.target.value)}
                                        className="rounded-md border border-zinc-700 bg-zinc-950 px-3 py-2 text-sm text-white outline-none"
                                        placeholder="Account code"
                                    />
                                    <input
                                        value={line.accountName}
                                        onChange={(event) => updateLine(index, "accountName", event.target.value)}
                                        className="rounded-md border border-zinc-700 bg-zinc-950 px-3 py-2 text-sm text-white outline-none"
                                        placeholder="Account name"
                                    />
                                    <select
                                        value={line.entrySide}
                                        onChange={(event) =>
                                            updateLine(index, "entrySide", event.target.value)
                                        }
                                        className="rounded-md border border-zinc-700 bg-zinc-950 px-3 py-2 text-sm text-white outline-none"
                                    >
                                        <option value="DEBIT">Debit</option>
                                        <option value="CREDIT">Credit</option>
                                    </select>
                                    <input
                                        type="number"
                                        min="0.01"
                                        step="0.01"
                                        value={line.amount}
                                        onChange={(event) => updateLine(index, "amount", event.target.value)}
                                        className="rounded-md border border-zinc-700 bg-zinc-950 px-3 py-2 text-sm text-white outline-none"
                                        placeholder="Amount"
                                    />
                                    <button
                                        type="button"
                                        onClick={() => removeLine(index)}
                                        disabled={lines.length <= 2}
                                        className="rounded-md border border-white/10 px-3 py-2 text-sm text-zinc-300 disabled:opacity-40"
                                    >
                                        Remove
                                    </button>
                                </div>
                            ))}
                        </div>

                        <div className="flex flex-wrap items-center gap-3">
                            <button
                                type="button"
                                onClick={addLine}
                                className="rounded-md border border-white/10 px-4 py-2 text-sm text-zinc-200 hover:bg-white/[0.05]"
                            >
                                Add line
                            </button>
                            <span className="text-sm text-zinc-400">
                                Debits: ${totalDebits.toFixed(2)} · Credits: ${totalCredits.toFixed(2)}
                            </span>
                            <span
                                className={[
                                    "rounded-full px-3 py-1 text-xs font-medium",
                                    adjustmentsBalanced
                                        ? "border border-emerald-400/30 bg-emerald-300/10 text-emerald-100"
                                        : "border border-amber-400/30 bg-amber-300/10 text-amber-100",
                                ].join(" ")}
                            >
                                {adjustmentsBalanced ? "Balanced" : "Needs balancing"}
                            </span>
                        </div>

                        <button
                            type="submit"
                            disabled={postingAdjustment}
                            className="rounded-md bg-emerald-300 px-4 py-2.5 text-sm font-semibold text-black disabled:opacity-50"
                        >
                            {postingAdjustment ? "Posting adjustment..." : "Post adjustment"}
                        </button>
                    </form>
                </SectionBand>

                <SectionBand
                    eyebrow="Ledger"
                    title="Recent journal activity"
                    description="Review the latest entries when something feels off, or confirm your latest adjustment landed where you expected."
                >
                    <div className="space-y-3">
                        {ledgerPreview.length === 0 ? (
                            <StatusBanner
                                tone="muted"
                                title="No ledger entries yet"
                                message="Once transactions or manual adjustments post, the ledger activity stream will populate here."
                            />
                        ) : (
                            ledgerPreview.map((entry) => (
                                <div
                                    key={entry.journalEntryId}
                                    className="rounded-lg border border-white/10 bg-white/[0.03] p-4"
                                >
                                    <div className="flex flex-wrap items-center justify-between gap-3">
                                        <div>
                                            <p className="text-sm font-semibold text-white">
                                                {entry.description}
                                            </p>
                                            <p className="mt-1 text-sm text-zinc-400">
                                                {entry.entryDate} · {entry.entryType.replaceAll("_", " ")}
                                                {entry.adjustmentReason
                                                    ? ` · ${entry.adjustmentReason}`
                                                    : ""}
                                            </p>
                                        </div>
                                        <span className="rounded-full border border-white/10 bg-white/[0.04] px-3 py-1 text-xs text-zinc-200">
                                            {entry.lines.length} line(s)
                                        </span>
                                    </div>
                                    <div className="mt-3 space-y-2">
                                        {entry.lines.map((line, index) => (
                                            <div
                                                key={`${entry.journalEntryId}-${index}`}
                                                className="flex flex-wrap items-center justify-between gap-3 rounded-md border border-white/6 bg-black/20 px-3 py-2 text-sm"
                                            >
                                                <span className="text-zinc-200">
                                                    {line.accountCode} · {line.accountName}
                                                </span>
                                                <span className="text-zinc-400">
                                                    {line.entrySide} · ${Number(line.amount).toFixed(2)}
                                                </span>
                                            </div>
                                        ))}
                                    </div>
                                </div>
                            ))
                        )}
                    </div>
                </SectionBand>
            </div>
        </main>
    );
}
