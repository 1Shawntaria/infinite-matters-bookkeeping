"use client";

import { useQuery, useQueryClient } from "@tanstack/react-query";
import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { Suspense, useEffect, useMemo, useState } from "react";
import {
    addCloseNote,
    approveClosePlaybookItem,
    addCloseSignoff,
    assignClosePlaybookItem,
    closePeriod,
    CloseAttestation,
    CloseChecklistSummary,
    ClosePlaybookItem,
    confirmCloseAttestation,
    completeClosePlaybookItem,
    createAdjustmentEntry,
    forceClosePeriod,
    getCloseAttestation,
    getCloseChecklist,
    LedgerEntrySummary,
    listCloseNotes,
    listClosePlaybookItems,
    listCloseSignoffs,
    listAccountingPeriods,
    listLedgerEntries,
    AccountingPeriodSummary,
    updateCloseAttestation,
} from "@/lib/api/close";
import { listFinancialAccounts, FinancialAccount } from "@/lib/api/accounts";
import { listMemberships, MembershipDetail } from "@/lib/api/access";
import { getDashboardSnapshot, DashboardSnapshot } from "@/lib/api/dashboard";
import { useOrganizationSession } from "@/lib/auth/session";
import { getCurrentUser, listOrganizations, OrganizationSummary } from "@/lib/api/auth";
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

type AccountSuggestion = {
    accountCode: string;
    accountName: string;
};

type AdjustmentTemplate = {
    id: string;
    label: string;
    description: string;
    entryTitle: string;
    adjustmentReason: string;
    lines: Array<{
        accountCode: string;
        entrySide: "DEBIT" | "CREDIT";
        amount: string;
    }>;
};

type SavedAdjustmentDraft = {
    id: string;
    name: string;
    entryDate: string;
    description: string;
    adjustmentReason: string;
    lines: AdjustmentLineDraft[];
    savedAt: string;
};

type SavedAdjustmentTemplate = {
    id: string;
    name: string;
    description: string;
    adjustmentReason: string;
    lines: AdjustmentLineDraft[];
    savedAt: string;
};

const EMPTY_LINE = (): AdjustmentLineDraft => ({
    accountCode: "",
    accountName: "",
    entrySide: "DEBIT",
    amount: "",
});

const ADJUSTMENT_TEMPLATES: AdjustmentTemplate[] = [
    {
        id: "accrued-expense",
        label: "Accrued expense",
        description: "Book an end-of-month expense that was incurred but not yet paid.",
        entryTitle: "Accrue month-end expense",
        adjustmentReason: "Recognize an unpaid month-end expense in the current period.",
        lines: [
            { accountCode: "6200", entrySide: "DEBIT", amount: "" },
            { accountCode: "2200", entrySide: "CREDIT", amount: "" },
        ],
    },
    {
        id: "prepaid-amortization",
        label: "Prepaid amortization",
        description: "Move a portion of a prepaid balance into the current month's expense.",
        entryTitle: "Amortize prepaid expense",
        adjustmentReason: "Release prepaid balance into the current reporting period.",
        lines: [
            { accountCode: "6100", entrySide: "DEBIT", amount: "" },
            { accountCode: "1200", entrySide: "CREDIT", amount: "" },
        ],
    },
    {
        id: "owner-reimbursement",
        label: "Owner reimbursement accrual",
        description: "Record an owner-paid business expense that still needs reimbursement.",
        entryTitle: "Accrue owner reimbursement",
        adjustmentReason: "Capture a business expense paid personally and owed back to the owner.",
        lines: [
            { accountCode: "6900", entrySide: "DEBIT", amount: "" },
            { accountCode: "2100", entrySide: "CREDIT", amount: "" },
        ],
    },
];

function closeDraftStorageKey(organizationId: string) {
    return `im-close-drafts:${organizationId}`;
}

function closeTemplateStorageKey(organizationId: string) {
    return `im-close-templates:${organizationId}`;
}

function ClosePageContent() {
    const searchParams = useSearchParams();
    const accountCodeParam = searchParams.get("accountCode") ?? "";
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
    const [draftName, setDraftName] = useState("");
    const [draftMessage, setDraftMessage] = useState("");
    const [savedDrafts, setSavedDrafts] = useState<SavedAdjustmentDraft[]>([]);
    const [templateName, setTemplateName] = useState("");
    const [templateMessage, setTemplateMessage] = useState("");
    const [savedTemplates, setSavedTemplates] = useState<SavedAdjustmentTemplate[]>([]);
    const [closeNote, setCloseNote] = useState("");
    const [noteMessage, setNoteMessage] = useState("");
    const [noteError, setNoteError] = useState("");
    const [savingNote, setSavingNote] = useState(false);
    const [signoffSummary, setSignoffSummary] = useState("");
    const [signoffMessage, setSignoffMessage] = useState("");
    const [signoffError, setSignoffError] = useState("");
    const [savingSignoff, setSavingSignoff] = useState(false);
    const [attestationOwnerUserId, setAttestationOwnerUserId] = useState("");
    const [attestationApproverUserId, setAttestationApproverUserId] = useState("");
    const [attestationSummary, setAttestationSummary] = useState("");
    const [attestationMessage, setAttestationMessage] = useState("");
    const [attestationError, setAttestationError] = useState("");
    const [savingAttestation, setSavingAttestation] = useState(false);
    const [confirmingAttestation, setConfirmingAttestation] = useState(false);
    const [lines, setLines] = useState<AdjustmentLineDraft[]>([
        accountCodeParam
            ? { ...EMPTY_LINE(), accountCode: accountCodeParam }
            : EMPTY_LINE(),
        EMPTY_LINE(),
    ]);

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
    const accountsQuery = useQuery<FinancialAccount[], Error>({
        queryKey: ["financialAccounts", organizationId],
        enabled: hydrated && Boolean(organizationId),
        queryFn: () => listFinancialAccounts(organizationId),
    });
    const organizationsQuery = useQuery<OrganizationSummary[], Error>({
        queryKey: ["workspaceOrganizations"],
        enabled: hydrated && Boolean(organizationId),
        queryFn: () => listOrganizations(),
    });
    const currentUserQuery = useQuery({
        queryKey: ["currentUser"],
        enabled: hydrated,
        queryFn: () => getCurrentUser(),
    });
    const closeNotesQuery = useQuery({
        queryKey: ["closeNotes", organizationId, selectedMonth],
        enabled: hydrated && Boolean(organizationId) && Boolean(selectedMonth),
        queryFn: () => listCloseNotes(organizationId, selectedMonth),
    });
    const closeSignoffsQuery = useQuery({
        queryKey: ["closeSignoffs", organizationId, selectedMonth],
        enabled: hydrated && Boolean(organizationId) && Boolean(selectedMonth),
        queryFn: () => listCloseSignoffs(organizationId, selectedMonth),
    });
    const closePlaybookQuery = useQuery<ClosePlaybookItem[], Error>({
        queryKey: ["closePlaybookItems", organizationId, selectedMonth],
        enabled: hydrated && Boolean(organizationId) && Boolean(selectedMonth),
        queryFn: () => listClosePlaybookItems(organizationId, selectedMonth),
    });
    const closeAttestationQuery = useQuery<CloseAttestation, Error>({
        queryKey: ["closeAttestation", organizationId, selectedMonth],
        enabled: hydrated && Boolean(organizationId) && Boolean(selectedMonth),
        queryFn: () => getCloseAttestation(organizationId, selectedMonth),
    });
    const membershipsQuery = useQuery<MembershipDetail[], Error>({
        queryKey: ["memberships", organizationId],
        enabled: hydrated && Boolean(organizationId),
        queryFn: () => listMemberships(organizationId),
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

    useEffect(() => {
        if (!organizationId) {
            setSavedDrafts([]);
            return;
        }

        const saved = window.localStorage.getItem(closeDraftStorageKey(organizationId));
        if (!saved) {
            setSavedDrafts([]);
            return;
        }

        try {
            const parsed = JSON.parse(saved) as SavedAdjustmentDraft[];
            setSavedDrafts(Array.isArray(parsed) ? parsed : []);
        } catch {
            setSavedDrafts([]);
        }
    }, [organizationId]);

    useEffect(() => {
        if (!organizationId) {
            setSavedTemplates([]);
            return;
        }

        const saved = window.localStorage.getItem(closeTemplateStorageKey(organizationId));
        if (!saved) {
            setSavedTemplates([]);
            return;
        }

        try {
            const parsed = JSON.parse(saved) as SavedAdjustmentTemplate[];
            setSavedTemplates(Array.isArray(parsed) ? parsed : []);
        } catch {
            setSavedTemplates([]);
        }
    }, [organizationId]);

    const loading =
        hydrated && organizationId
            ? dashboardQuery.isLoading ||
              periodsQuery.isLoading ||
              ledgerQuery.isLoading ||
              accountsQuery.isLoading ||
              organizationsQuery.isLoading ||
              currentUserQuery.isLoading ||
              closePlaybookQuery.isLoading ||
              closeAttestationQuery.isLoading ||
              membershipsQuery.isLoading ||
              closeNotesQuery.isLoading ||
              closeSignoffsQuery.isLoading ||
              (Boolean(selectedMonth) && checklistQuery.isLoading)
            : false;
    const queryError =
        dashboardQuery.error?.message ??
        periodsQuery.error?.message ??
        checklistQuery.error?.message ??
        ledgerQuery.error?.message ??
        accountsQuery.error?.message ??
        organizationsQuery.error?.message ??
        currentUserQuery.error?.message ??
        closePlaybookQuery.error?.message ??
        closeAttestationQuery.error?.message ??
        membershipsQuery.error?.message ??
        closeNotesQuery.error?.message ??
        closeSignoffsQuery.error?.message ??
        "";

    const periods = periodsQuery.data ?? [];
    const checklist = checklistQuery.data ?? null;
    const ledgerEntries = useMemo(
        () => ledgerQuery.data ?? [],
        [ledgerQuery.data]
    );
    const financialAccounts = useMemo(
        () => accountsQuery.data ?? [],
        [accountsQuery.data]
    );
    const currentWorkspaceRole =
        organizationsQuery.data?.find((organization) => organization.id === organizationId)?.role ??
        null;
    const canManageClose =
        currentWorkspaceRole === "OWNER" || currentWorkspaceRole === "ADMIN";
    const currentPeriod = periods.find(
        (period) => period.periodStart.slice(0, 7) === selectedMonth
    ) ?? null;
    const completeChecklistItems =
        checklist?.items.filter((item) => item.complete).length ?? 0;
    const totalChecklistItems = checklist?.items.length ?? 0;
    const ledgerPreview = ledgerEntries.slice(0, 8);
    const accountSuggestions = useMemo(() => {
        const suggestions = new Map<string, AccountSuggestion>();
        const commonAccounts: AccountSuggestion[] = [
            { accountCode: "1100", accountName: "Accounts Receivable" },
            { accountCode: "1200", accountName: "Prepaid Expenses" },
            { accountCode: "2100", accountName: "Accounts Payable" },
            { accountCode: "2200", accountName: "Accrued Expenses" },
            { accountCode: "4000", accountName: "Income" },
            { accountCode: "6100", accountName: "Software Expense" },
            { accountCode: "6200", accountName: "Travel Expense" },
            { accountCode: "6300", accountName: "Meals Expense" },
            { accountCode: "6900", accountName: "Miscellaneous Expense" },
        ];

        for (const account of commonAccounts) {
            suggestions.set(account.accountCode, account);
        }

        for (const financialAccount of financialAccounts) {
            const mappedCode =
                financialAccount.accountType === "BANK"
                    ? "1000"
                    : financialAccount.accountType === "CREDIT_CARD"
                      ? "2000"
                      : financialAccount.accountType === "CASH"
                        ? "1010"
                        : "2300";
            suggestions.set(mappedCode, {
                accountCode: mappedCode,
                accountName: financialAccount.name,
            });
        }

        for (const entry of ledgerEntries) {
            for (const line of entry.lines) {
                suggestions.set(line.accountCode, {
                    accountCode: line.accountCode,
                    accountName: line.accountName,
                });
            }
        }

        return Array.from(suggestions.values()).sort((left, right) =>
            left.accountCode.localeCompare(right.accountCode)
        );
    }, [financialAccounts, ledgerEntries]);
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
    const closeNotes = closeNotesQuery.data ?? [];
    const closeSignoffs = closeSignoffsQuery.data ?? [];
    const closePlaybookItems = closePlaybookQuery.data ?? [];
    const closeAttestation = closeAttestationQuery.data ?? null;
    const memberships = membershipsQuery.data ?? [];
    const monthAdjustments = useMemo(
        () =>
            ledgerEntries.filter(
                (entry) =>
                    entry.entryType === "ADJUSTMENT" &&
                    (!selectedMonth || entry.entryDate.startsWith(selectedMonth))
            ),
        [ledgerEntries, selectedMonth]
    );
    const blockingChecklistItems = (checklist?.items ?? []).filter((item) => !item.complete);
    const currentUser = currentUserQuery.data ?? null;
    const currentUserHasSignedOff = closeSignoffs.some(
        (signoff) => signoff.actorUserId && signoff.actorUserId === currentUser?.id
    );
    const canSignOffClose = canManageClose && Boolean(selectedMonth);
    const attestationReady = Boolean(closeAttestation?.summary?.trim());

    useEffect(() => {
        setAttestationOwnerUserId(closeAttestation?.closeOwner?.id ?? "");
        setAttestationApproverUserId(closeAttestation?.closeApprover?.id ?? "");
        setAttestationSummary(closeAttestation?.summary ?? "");
    }, [
        closeAttestation?.closeApprover?.id,
        closeAttestation?.closeOwner?.id,
        closeAttestation?.summary,
    ]);

    useEffect(() => {
        if (!accountCodeParam) {
            return;
        }

        setLines((current) => {
            const nextLines = [...current];
            const firstLine = nextLines[0] ?? EMPTY_LINE();
            const matchedSuggestion = accountSuggestions.find(
                (suggestion) => suggestion.accountCode === accountCodeParam
            );

            nextLines[0] = {
                ...firstLine,
                accountCode: accountCodeParam,
                accountName:
                    matchedSuggestion?.accountName ??
                    firstLine.accountName,
            };

            return nextLines;
        });
    }, [accountCodeParam, accountSuggestions]);

    function updateLine(index: number, field: keyof AdjustmentLineDraft, value: string) {
        setLines((current) =>
            current.map((line, lineIndex) =>
                lineIndex === index ? { ...line, [field]: value } : line
            )
        );
    }

    function applySuggestionToLine(index: number, suggestionValue: string) {
        const [accountCode] = suggestionValue.split(" - ");
        const matchedSuggestion =
            accountSuggestions.find(
                (suggestion) =>
                    suggestion.accountCode === accountCode ||
                    suggestion.accountName === suggestionValue
            ) ??
            accountSuggestions.find(
                (suggestion) =>
                    suggestion.accountCode === suggestionValue ||
                    suggestion.accountName === suggestionValue
            );

        if (!matchedSuggestion) {
            return;
        }

        setLines((current) =>
            current.map((line, lineIndex) =>
                lineIndex === index
                    ? {
                          ...line,
                          accountCode: matchedSuggestion.accountCode,
                          accountName: matchedSuggestion.accountName,
                      }
                    : line
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

    function applyTemplate(template: AdjustmentTemplate) {
        const templateLines = template.lines.map((line) => {
            const matchedSuggestion = accountSuggestions.find(
                (suggestion) => suggestion.accountCode === line.accountCode
            );
            return {
                accountCode: line.accountCode,
                accountName: matchedSuggestion?.accountName ?? "",
                entrySide: line.entrySide,
                amount: line.amount,
            };
        });

        setDescription(template.entryTitle);
        setAdjustmentReason(template.adjustmentReason);
        setLines(templateLines);
        setAdjustmentError("");
        setAdjustmentSuccess("");
    }

    async function refreshCloseQueries(activeOrganizationId: string) {
        await queryClient.invalidateQueries({ queryKey: ["dashboardSnapshot", activeOrganizationId] });
        await queryClient.invalidateQueries({ queryKey: ["accountingPeriods", activeOrganizationId] });
        await queryClient.invalidateQueries({
            queryKey: ["closeChecklist", activeOrganizationId, selectedMonth],
        });
        await queryClient.invalidateQueries({ queryKey: ["ledgerEntries", activeOrganizationId] });
        await queryClient.invalidateQueries({ queryKey: ["reconciliationDashboard", activeOrganizationId] });
        await queryClient.invalidateQueries({
            queryKey: ["closeNotes", activeOrganizationId, selectedMonth],
        });
        await queryClient.invalidateQueries({
            queryKey: ["closeSignoffs", activeOrganizationId, selectedMonth],
        });
        await queryClient.invalidateQueries({
            queryKey: ["closePlaybookItems", activeOrganizationId, selectedMonth],
        });
        await queryClient.invalidateQueries({
            queryKey: ["closeAttestation", activeOrganizationId, selectedMonth],
        });
    }

    function persistDrafts(nextDrafts: SavedAdjustmentDraft[]) {
        if (!organizationId) {
            return;
        }
        setSavedDrafts(nextDrafts);
        window.localStorage.setItem(
            closeDraftStorageKey(organizationId),
            JSON.stringify(nextDrafts)
        );
    }

    function persistTemplates(nextTemplates: SavedAdjustmentTemplate[]) {
        if (!organizationId) {
            return;
        }
        setSavedTemplates(nextTemplates);
        window.localStorage.setItem(
            closeTemplateStorageKey(organizationId),
            JSON.stringify(nextTemplates)
        );
    }

    function saveCurrentDraft() {
        if (!organizationId) {
            setDraftMessage("Sign back in before saving a draft.");
            return;
        }
        if (!draftName.trim()) {
            setDraftMessage("Give this adjustment draft a short name first.");
            return;
        }

        const draft: SavedAdjustmentDraft = {
            id: `${Date.now()}`,
            name: draftName.trim(),
            entryDate,
            description,
            adjustmentReason,
            lines,
            savedAt: new Date().toISOString(),
        };
        persistDrafts([draft, ...savedDrafts].slice(0, 12));
        setDraftName("");
        setDraftMessage(`Saved draft "${draft.name}".`);
    }

    function saveCurrentTemplate() {
        if (!organizationId) {
            setTemplateMessage("Sign back in before saving a template.");
            return;
        }
        if (!templateName.trim()) {
            setTemplateMessage("Give this template a short name first.");
            return;
        }

        const template: SavedAdjustmentTemplate = {
            id: `${Date.now()}`,
            name: templateName.trim(),
            description: description.trim() || templateName.trim(),
            adjustmentReason: adjustmentReason.trim(),
            lines,
            savedAt: new Date().toISOString(),
        };
        persistTemplates([template, ...savedTemplates].slice(0, 16));
        setTemplateName("");
        setTemplateMessage(`Saved template "${template.name}".`);
    }

    function applySavedDraft(draft: SavedAdjustmentDraft) {
        setEntryDate(draft.entryDate);
        setDescription(draft.description);
        setAdjustmentReason(draft.adjustmentReason);
        setLines(draft.lines.length > 0 ? draft.lines : [EMPTY_LINE(), EMPTY_LINE()]);
        setDraftMessage(`Loaded draft "${draft.name}".`);
        setAdjustmentError("");
        setAdjustmentSuccess("");
    }

    function applySavedTemplate(template: SavedAdjustmentTemplate) {
        setDescription(template.description);
        setAdjustmentReason(template.adjustmentReason);
        setLines(template.lines.length > 0 ? template.lines : [EMPTY_LINE(), EMPTY_LINE()]);
        setTemplateMessage(`Loaded template "${template.name}".`);
        setAdjustmentError("");
        setAdjustmentSuccess("");
    }

    function deleteSavedDraft(draftId: string) {
        const draftToDelete = savedDrafts.find((draft) => draft.id === draftId);
        persistDrafts(savedDrafts.filter((draft) => draft.id !== draftId));
        setDraftMessage(
            draftToDelete ? `Removed draft "${draftToDelete.name}".` : "Draft removed."
        );
    }

    function deleteSavedTemplate(templateId: string) {
        const templateToDelete = savedTemplates.find((template) => template.id === templateId);
        persistTemplates(savedTemplates.filter((template) => template.id !== templateId));
        setTemplateMessage(
            templateToDelete
                ? `Removed template "${templateToDelete.name}".`
                : "Template removed."
        );
    }

    async function handleAddCloseNote(event: React.FormEvent<HTMLFormElement>) {
        event.preventDefault();
        if (!organizationId || !selectedMonth) {
            setNoteError("Choose a month before adding a close note.");
            return;
        }
        if (!closeNote.trim()) {
            setNoteError("Add a note before saving.");
            return;
        }

        setSavingNote(true);
        setNoteError("");
        setNoteMessage("");

        try {
            await addCloseNote(organizationId, selectedMonth, closeNote.trim());
            await queryClient.invalidateQueries({
                queryKey: ["closeNotes", organizationId, selectedMonth],
            });
            setCloseNote("");
            setNoteMessage("Close note saved to the month-end history.");
        } catch (err) {
            setNoteError(err instanceof Error ? err.message : "Unable to save close note.");
        } finally {
            setSavingNote(false);
        }
    }

    async function handleAddCloseSignoff(event: React.FormEvent<HTMLFormElement>) {
        event.preventDefault();
        if (!organizationId || !selectedMonth) {
            setSignoffError("Choose a month before signing off.");
            return;
        }
        if (!canManageClose) {
            setSignoffError("Only owners and admins can approve a month-end close.");
            return;
        }
        if (!canSignOffClose) {
            setSignoffError("Choose a month before signing off.");
            return;
        }
        if (!signoffSummary.trim()) {
            setSignoffError("Add a short approval summary before signing off.");
            return;
        }

        setSavingSignoff(true);
        setSignoffError("");
        setSignoffMessage("");

        try {
            await addCloseSignoff(organizationId, selectedMonth, signoffSummary.trim());
            await queryClient.invalidateQueries({
                queryKey: ["closeSignoffs", organizationId, selectedMonth],
            });
            setSignoffSummary("");
            setSignoffMessage("Close sign-off recorded for the selected month.");
        } catch (err) {
            setSignoffError(err instanceof Error ? err.message : "Unable to record close sign-off.");
        } finally {
            setSavingSignoff(false);
        }
    }

    async function handleSaveAttestation(event: React.FormEvent<HTMLFormElement>) {
        event.preventDefault();
        if (!organizationId || !selectedMonth) {
            setAttestationError("Choose a month before saving the close attestation.");
            return;
        }
        if (!canManageClose) {
            setAttestationError("Only owners and admins can update the month-end attestation.");
            return;
        }

        setSavingAttestation(true);
        setAttestationError("");
        setAttestationMessage("");

        try {
            await updateCloseAttestation(organizationId, {
                month: selectedMonth,
                closeOwnerUserId: attestationOwnerUserId || null,
                closeApproverUserId: attestationApproverUserId || null,
                summary: attestationSummary,
            });
            await queryClient.invalidateQueries({
                queryKey: ["closeAttestation", organizationId, selectedMonth],
            });
            setAttestationMessage("Month-end attestation plan saved.");
        } catch (err) {
            setAttestationError(err instanceof Error ? err.message : "Unable to save the close attestation.");
        } finally {
            setSavingAttestation(false);
        }
    }

    async function handleConfirmAttestation() {
        if (!organizationId || !selectedMonth) {
            setAttestationError("Choose a month before confirming the close attestation.");
            return;
        }
        if (!canManageClose) {
            setAttestationError("Only owners and admins can confirm the month-end attestation.");
            return;
        }
        if (!attestationReady) {
            setAttestationError("Add an attestation summary before confirming the month-end record.");
            return;
        }

        setConfirmingAttestation(true);
        setAttestationError("");
        setAttestationMessage("");

        try {
            await confirmCloseAttestation(organizationId, selectedMonth);
            await queryClient.invalidateQueries({
                queryKey: ["closeAttestation", organizationId, selectedMonth],
            });
            setAttestationMessage("Month-end attestation confirmed.");
        } catch (err) {
            setAttestationError(err instanceof Error ? err.message : "Unable to confirm the close attestation.");
        } finally {
            setConfirmingAttestation(false);
        }
    }

    async function handleAssignPlaybookItem(
        templateItemId: string,
        assigneeUserId: string | null,
        approverUserId: string | null
    ) {
        if (!organizationId || !selectedMonth) {
            return;
        }
        try {
            await assignClosePlaybookItem(organizationId, templateItemId, {
                month: selectedMonth,
                assigneeUserId,
                approverUserId,
            });
            await queryClient.invalidateQueries({
                queryKey: ["closePlaybookItems", organizationId, selectedMonth],
            });
            setCloseSuccess("Recurring close playbook routing updated.");
        } catch (err) {
            setCloseError(err instanceof Error ? err.message : "Unable to update recurring close playbook routing.");
        }
    }

    async function handleCompletePlaybookItem(templateItemId: string, marked: boolean) {
        if (!organizationId || !selectedMonth) {
            return;
        }
        try {
            await completeClosePlaybookItem(organizationId, templateItemId, selectedMonth, marked);
            await queryClient.invalidateQueries({
                queryKey: ["closePlaybookItems", organizationId, selectedMonth],
            });
            setCloseSuccess(marked ? "Recurring close playbook item completed." : "Recurring close playbook item reopened.");
        } catch (err) {
            setCloseError(err instanceof Error ? err.message : "Unable to update recurring close playbook item.");
        }
    }

    async function handleApprovePlaybookItem(templateItemId: string, marked: boolean) {
        if (!organizationId || !selectedMonth) {
            return;
        }
        try {
            await approveClosePlaybookItem(organizationId, templateItemId, selectedMonth, marked);
            await queryClient.invalidateQueries({
                queryKey: ["closePlaybookItems", organizationId, selectedMonth],
            });
            setCloseSuccess(marked ? "Recurring close playbook item approved." : "Recurring close playbook approval cleared.");
        } catch (err) {
            setCloseError(err instanceof Error ? err.message : "Unable to update recurring close playbook approval.");
        }
    }

    async function handleClosePeriod() {
        if (!organizationId || !selectedMonth) return;
        if (!canManageClose) {
            setCloseError("Only owners and admins can close a period.");
            return;
        }
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
        if (!canManageClose) {
            setCloseError("Only owners and admins can force close a period.");
            return;
        }
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
        if (!canManageClose) {
            setAdjustmentError("Only owners and admins can post adjustments.");
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
                        <SummaryMetric
                            label="Attestation"
                            value={closeAttestation?.attested ? "Confirmed" : "Pending"}
                            detail={
                                closeAttestation?.attested
                                    ? "Owner-level month-end accountability has been captured."
                                    : "Month-level ownership and certification still need to be confirmed."
                            }
                            tone={closeAttestation?.attested ? "success" : "default"}
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
                eyebrow="Handoff summary"
                title="What the next reviewer needs to know"
                description="Use this as the fast read before someone picks up the month-end baton."
            >
                <div className="grid gap-4 lg:grid-cols-[1.05fr_0.95fr]">
                    <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
                        <SummaryMetric
                            label="Open blockers"
                            value={`${blockingChecklistItems.length}`}
                            detail={
                                blockingChecklistItems.length === 0
                                    ? "No close blockers are left in the checklist."
                                    : "Checklist items still blocking a clean close."
                            }
                            tone={blockingChecklistItems.length === 0 ? "success" : "warning"}
                        />
                        <SummaryMetric
                            label="Month adjustments"
                            value={`${monthAdjustments.length}`}
                            detail="Manual entries posted in the selected close month."
                        />
                        <SummaryMetric
                            label="Close notes"
                            value={`${closeNotes.length}`}
                            detail="Context saved for reviewers, approvers, and future you."
                        />
                        <SummaryMetric
                            label="Sign-offs"
                            value={`${closeSignoffs.length}`}
                            detail={
                                closeSignoffs.length === 0
                                    ? "No formal approvals recorded yet."
                                    : "Approval trail captured for this month."
                            }
                            tone={closeSignoffs.length > 0 ? "success" : "default"}
                        />
                    </div>
                    <div className="rounded-lg border border-white/10 bg-black/20 p-4">
                        <p className="text-sm font-semibold text-white">Close posture</p>
                        <div className="mt-3 space-y-2 text-sm text-zinc-300">
                            <p>
                                Period status:{" "}
                                <span className="font-medium text-white">
                                    {currentPeriod?.status ?? "OPEN"}
                                </span>
                            </p>
                            <p>
                                Latest method:{" "}
                                <span className="font-medium text-white">
                                    {currentPeriod?.closeMethod ?? "Not closed yet"}
                                </span>
                            </p>
                            {currentPeriod?.overrideReason ? (
                                <p>
                                    Override reason:{" "}
                                    <span className="text-zinc-100">{currentPeriod.overrideReason}</span>
                                </p>
                            ) : null}
                            {blockingChecklistItems.length > 0 ? (
                                <ul className="mt-3 space-y-2 text-zinc-400">
                                    {blockingChecklistItems.slice(0, 3).map((item) => (
                                        <li key={item.label}>• {item.label}</li>
                                    ))}
                                </ul>
                            ) : (
                                <p className="text-emerald-200">
                                    Close blockers are cleared for the selected month.
                                </p>
                            )}
                        </div>
                    </div>
                </div>
            </SectionBand>

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
                                disabled={
                                    !canManageClose ||
                                    closingPeriod ||
                                    forceClosingPeriod ||
                                    !selectedMonth
                                }
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
                                    disabled={!canManageClose}
                                    className="w-full rounded-md border border-zinc-700 bg-zinc-950 px-3 py-2 text-white outline-none"
                                    placeholder="Explain why the period is being force-closed."
                                />
                            </label>
                            <button
                                type="button"
                                onClick={handleForceClosePeriod}
                                disabled={
                                    !canManageClose ||
                                    forceClosingPeriod ||
                                    closingPeriod ||
                                    !selectedMonth
                                }
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
                    {!canManageClose ? (
                        <div className="mb-4">
                            <StatusBanner
                                tone="muted"
                                title="Read-only close access"
                                message="You can inspect close readiness, but only workspace owners and admins can post adjustments or close periods."
                            />
                        </div>
                    ) : null}
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

                    <div className="mb-4 rounded-lg border border-white/10 bg-black/20 p-4">
                        <p className="text-xs uppercase tracking-[0.18em] text-zinc-500">
                            Adjustment templates
                        </p>
                        <div className="mt-3 grid gap-3 lg:grid-cols-3">
                            {ADJUSTMENT_TEMPLATES.map((template) => (
                                <button
                                    key={template.id}
                                    type="button"
                                    onClick={() => applyTemplate(template)}
                                    disabled={!canManageClose}
                                    className="rounded-lg border border-white/10 bg-white/[0.03] p-4 text-left hover:border-white/20"
                                >
                                    <p className="text-sm font-semibold text-white">
                                        {template.label}
                                    </p>
                                    <p className="mt-2 text-sm text-zinc-400">
                                        {template.description}
                                    </p>
                                </button>
                            ))}
                        </div>
                    </div>

                    <div className="mb-4 rounded-lg border border-white/10 bg-black/20 p-4">
                        <div className="flex flex-wrap items-start justify-between gap-4">
                            <div>
                                <p className="text-xs uppercase tracking-[0.18em] text-zinc-500">
                                    Saved adjustment drafts
                                </p>
                                <p className="mt-2 text-sm text-zinc-400">
                                    Keep partial close work around when the month gets interrupted.
                                </p>
                            </div>
                            <div className="flex w-full gap-3 sm:w-auto">
                                <label className="sr-only" htmlFor="draft-name">
                                    Draft name
                                </label>
                                <input
                                    id="draft-name"
                                    value={draftName}
                                    onChange={(event) => setDraftName(event.target.value)}
                                    disabled={!canManageClose}
                                    className="min-w-[14rem] rounded-md border border-zinc-700 bg-zinc-950 px-3 py-2 text-sm text-white outline-none"
                                    placeholder="April accrual package"
                                />
                                <button
                                    type="button"
                                    onClick={saveCurrentDraft}
                                    disabled={!canManageClose}
                                    className="rounded-md border border-white/10 px-4 py-2 text-sm text-zinc-200 hover:bg-white/[0.05] disabled:opacity-50"
                                >
                                    Save draft
                                </button>
                            </div>
                        </div>
                        {draftMessage ? (
                            <p className="mt-3 text-sm text-emerald-200">{draftMessage}</p>
                        ) : null}
                        <div className="mt-4 space-y-3">
                            {savedDrafts.length === 0 ? (
                                <p className="text-sm text-zinc-500">
                                    No saved drafts yet for this workspace.
                                </p>
                            ) : (
                                savedDrafts.map((draft) => (
                                    <div
                                        key={draft.id}
                                        className="flex flex-wrap items-center justify-between gap-3 rounded-lg border border-white/10 bg-white/[0.03] p-3"
                                    >
                                        <div>
                                            <p className="text-sm font-semibold text-white">{draft.name}</p>
                                            <p className="mt-1 text-xs text-zinc-400">
                                                Saved{" "}
                                                {new Date(draft.savedAt).toLocaleString("en-US", {
                                                    month: "short",
                                                    day: "numeric",
                                                    hour: "numeric",
                                                    minute: "2-digit",
                                                })}
                                            </p>
                                        </div>
                                        <div className="flex gap-2">
                                            <button
                                                type="button"
                                                onClick={() => applySavedDraft(draft)}
                                                className="rounded-md border border-white/10 px-3 py-2 text-sm text-zinc-200 hover:bg-white/[0.05]"
                                            >
                                                Load draft
                                            </button>
                                            <button
                                                type="button"
                                                onClick={() => deleteSavedDraft(draft.id)}
                                                className="rounded-md border border-white/10 px-3 py-2 text-sm text-zinc-300 hover:bg-white/[0.05]"
                                            >
                                                Delete
                                            </button>
                                        </div>
                                    </div>
                                ))
                            )}
                        </div>
                    </div>

                    <div className="mb-4 rounded-lg border border-white/10 bg-black/20 p-4">
                        <div className="flex flex-wrap items-start justify-between gap-4">
                            <div>
                                <p className="text-xs uppercase tracking-[0.18em] text-zinc-500">
                                    Reusable close templates
                                </p>
                                <p className="mt-2 text-sm text-zinc-400">
                                    Save patterns your team posts every month so nobody has to rebuild them from scratch.
                                </p>
                            </div>
                            <div className="flex w-full gap-3 sm:w-auto">
                                <label className="sr-only" htmlFor="template-name">
                                    Template name
                                </label>
                                <input
                                    id="template-name"
                                    value={templateName}
                                    onChange={(event) => setTemplateName(event.target.value)}
                                    disabled={!canManageClose}
                                    className="min-w-[14rem] rounded-md border border-zinc-700 bg-zinc-950 px-3 py-2 text-sm text-white outline-none"
                                    placeholder="Monthly travel accrual"
                                />
                                <button
                                    type="button"
                                    onClick={saveCurrentTemplate}
                                    disabled={!canManageClose}
                                    className="rounded-md border border-white/10 px-4 py-2 text-sm text-zinc-200 hover:bg-white/[0.05] disabled:opacity-50"
                                >
                                    Save template
                                </button>
                            </div>
                        </div>
                        {templateMessage ? (
                            <p className="mt-3 text-sm text-emerald-200">{templateMessage}</p>
                        ) : null}
                        <div className="mt-4 space-y-3">
                            {savedTemplates.length === 0 ? (
                                <p className="text-sm text-zinc-500">
                                    No reusable templates saved yet for this workspace.
                                </p>
                            ) : (
                                savedTemplates.map((template) => (
                                    <div
                                        key={template.id}
                                        className="flex flex-wrap items-center justify-between gap-3 rounded-lg border border-white/10 bg-white/[0.03] p-3"
                                    >
                                        <div>
                                            <p className="text-sm font-semibold text-white">{template.name}</p>
                                            <p className="mt-1 text-xs text-zinc-400">
                                                {template.lines.length} line(s) · saved{" "}
                                                {new Date(template.savedAt).toLocaleDateString("en-US", {
                                                    month: "short",
                                                    day: "numeric",
                                                })}
                                            </p>
                                        </div>
                                        <div className="flex gap-2">
                                            <button
                                                type="button"
                                                onClick={() => applySavedTemplate(template)}
                                                className="rounded-md border border-white/10 px-3 py-2 text-sm text-zinc-200 hover:bg-white/[0.05]"
                                            >
                                                Load template
                                            </button>
                                            <button
                                                type="button"
                                                onClick={() => deleteSavedTemplate(template.id)}
                                                className="rounded-md border border-white/10 px-3 py-2 text-sm text-zinc-300 hover:bg-white/[0.05]"
                                            >
                                                Delete
                                            </button>
                                        </div>
                                    </div>
                                ))
                            )}
                        </div>
                    </div>

                    <form className="space-y-4" onSubmit={handlePostAdjustment}>
                        <div className="grid gap-4 md:grid-cols-2">
                            <label className="space-y-2 text-sm text-zinc-300">
                                <span>Entry date</span>
                                <input
                                    type="date"
                                    value={entryDate}
                                    onChange={(event) => setEntryDate(event.target.value)}
                                    disabled={!canManageClose}
                                    className="w-full rounded-md border border-zinc-700 bg-zinc-950 px-3 py-2 text-white outline-none"
                                    required
                                />
                            </label>
                            <label className="space-y-2 text-sm text-zinc-300">
                                <span>Description</span>
                                <input
                                    value={description}
                                    onChange={(event) => setDescription(event.target.value)}
                                    disabled={!canManageClose}
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
                                disabled={!canManageClose}
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
                                        onChange={(event) => {
                                            updateLine(index, "accountCode", event.target.value);
                                            applySuggestionToLine(index, event.target.value);
                                        }}
                                        disabled={!canManageClose}
                                        list={`account-code-suggestions-${index}`}
                                        className="rounded-md border border-zinc-700 bg-zinc-950 px-3 py-2 text-sm text-white outline-none"
                                        placeholder="Account code"
                                    />
                                    <datalist id={`account-code-suggestions-${index}`}>
                                        {accountSuggestions.map((suggestion) => (
                                            <option
                                                key={`${suggestion.accountCode}-${suggestion.accountName}`}
                                                value={suggestion.accountCode}
                                            >
                                                {suggestion.accountName}
                                            </option>
                                        ))}
                                    </datalist>
                                    <input
                                        value={line.accountName}
                                        onChange={(event) => {
                                            updateLine(index, "accountName", event.target.value);
                                            applySuggestionToLine(index, event.target.value);
                                        }}
                                        disabled={!canManageClose}
                                        list={`account-name-suggestions-${index}`}
                                        className="rounded-md border border-zinc-700 bg-zinc-950 px-3 py-2 text-sm text-white outline-none"
                                        placeholder="Account name"
                                    />
                                    <datalist id={`account-name-suggestions-${index}`}>
                                        {accountSuggestions.map((suggestion) => (
                                            <option
                                                key={`${suggestion.accountCode}-${suggestion.accountName}-name`}
                                                value={suggestion.accountName}
                                            >
                                                {suggestion.accountCode}
                                            </option>
                                        ))}
                                    </datalist>
                                    <select
                                        value={line.entrySide}
                                        onChange={(event) =>
                                            updateLine(index, "entrySide", event.target.value)
                                        }
                                        disabled={!canManageClose}
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
                                        disabled={!canManageClose}
                                        className="rounded-md border border-zinc-700 bg-zinc-950 px-3 py-2 text-sm text-white outline-none"
                                        placeholder="Amount"
                                    />
                                    <button
                                        type="button"
                                        onClick={() => removeLine(index)}
                                        disabled={!canManageClose || lines.length <= 2}
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
                                disabled={!canManageClose}
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
                        <div className="rounded-lg border border-white/10 bg-black/20 p-4">
                            <p className="text-xs uppercase tracking-[0.18em] text-zinc-500">
                                Suggested accounts
                            </p>
                            {accountCodeParam ? (
                                <p className="mt-2 text-sm text-zinc-400">
                                    Adjustment lines are prefilled from account{" "}
                                    <span className="font-medium text-zinc-200">
                                        {accountCodeParam}
                                    </span>
                                    . You can keep it, replace it, or pair it with an offsetting line.
                                </p>
                            ) : null}
                            <div className="mt-3 flex flex-wrap gap-2">
                                {accountSuggestions.slice(0, 10).map((suggestion) => (
                                    <span
                                        key={`${suggestion.accountCode}-chip`}
                                        className="rounded-full border border-white/10 bg-white/[0.04] px-3 py-1 text-xs text-zinc-300"
                                    >
                                        {suggestion.accountCode} · {suggestion.accountName}
                                    </span>
                                ))}
                            </div>
                        </div>

                        <button
                            type="submit"
                            disabled={!canManageClose || postingAdjustment}
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
                                                <div className="flex flex-wrap items-center gap-3">
                                                    <span className="text-zinc-200">
                                                        {line.accountCode} · {line.accountName}
                                                    </span>
                                                    <Link
                                                        href={`/accounting?accountCode=${encodeURIComponent(line.accountCode)}`}
                                                        className="rounded-md border border-white/10 px-2.5 py-1 text-xs text-zinc-300 hover:bg-white/[0.05]"
                                                    >
                                                        View {line.accountCode}
                                                    </Link>
                                                    {entry.transactionId ? (
                                                        <Link
                                                            href={`/activity?lane=IMPORT&entityId=${encodeURIComponent(entry.transactionId)}&label=${encodeURIComponent(entry.description)}`}
                                                            className="rounded-md border border-white/10 px-2.5 py-1 text-xs text-zinc-300 hover:bg-white/[0.05]"
                                                        >
                                                            Source activity
                                                        </Link>
                                                    ) : null}
                                                </div>
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

            <SectionBand
                eyebrow="Close notes"
                title="Month-end notes and handoff context"
                description="Capture what changed, what still needs eyes, and why exceptions were acceptable so nobody has to reconstruct the story later."
            >
                <div className="grid gap-6 xl:grid-cols-[0.9fr_1.1fr]">
                    <div>
                        {noteError ? (
                            <div className="mb-4">
                                <StatusBanner tone="error" title="Note not saved" message={noteError} />
                            </div>
                        ) : null}
                        {noteMessage ? (
                            <div className="mb-4">
                                <StatusBanner tone="success" title="Note saved" message={noteMessage} />
                            </div>
                        ) : null}
                        <form className="space-y-3" onSubmit={handleAddCloseNote}>
                            <label className="space-y-2 text-sm text-zinc-300">
                                <span>Close note for {selectedMonth || "this month"}</span>
                                <textarea
                                    value={closeNote}
                                    onChange={(event) => setCloseNote(event.target.value)}
                                    rows={5}
                                    className="w-full rounded-md border border-zinc-700 bg-zinc-950 px-3 py-2 text-white outline-none"
                                    placeholder="Document open questions, approvals, adjustments posted, or what the next reviewer should know."
                                />
                            </label>
                            <button
                                type="submit"
                                disabled={savingNote}
                                className="rounded-md border border-white/10 px-4 py-2.5 text-sm font-semibold text-zinc-100 hover:bg-white/[0.05] disabled:opacity-50"
                            >
                                {savingNote ? "Saving note..." : "Save close note"}
                            </button>
                        </form>
                    </div>
                    <div className="space-y-3">
                        {closeNotes.length === 0 ? (
                            <StatusBanner
                                tone="muted"
                                title="No notes for this month yet"
                                message="Use notes to leave context for reviewers, approvers, and the next person picking up close work."
                            />
                        ) : (
                            closeNotes.map((note) => (
                                <div
                                    key={note.id}
                                    className="rounded-lg border border-white/10 bg-white/[0.03] p-4"
                                >
                                    <div className="flex flex-wrap items-center justify-between gap-3">
                                        <p className="text-xs uppercase tracking-[0.16em] text-zinc-500">
                                            {note.eventType.replaceAll("_", " ")}
                                        </p>
                                        <p className="text-xs text-zinc-500">
                                            {new Date(note.createdAt).toLocaleString("en-US", {
                                                month: "short",
                                                day: "numeric",
                                                hour: "numeric",
                                                minute: "2-digit",
                                            })}
                                        </p>
                                    </div>
                                    <p className="mt-3 text-sm leading-6 text-zinc-200">{note.details}</p>
                                </div>
                            ))
                        )}
                    </div>
                </div>
            </SectionBand>

            <SectionBand
                eyebrow="Recurring close playbook"
                title="Monthly ownership for recurring checks"
                description="Turn the standing close playbook into real month-specific work: route each item, mark it complete, and capture approval when another set of eyes should sign off on it."
            >
                <div className="space-y-3">
                    {closePlaybookItems.length === 0 ? (
                        <StatusBanner
                            tone="muted"
                            title="No recurring close playbook items yet"
                            message="Add recurring checks in Settings so each month can be routed, completed, and approved inside the close workspace."
                        />
                    ) : (
                        closePlaybookItems.map((item) => {
                            const currentUserId = currentUser?.id ?? "";
                            const canCompleteItem =
                                canManageClose ||
                                (item.assignee?.id != null && item.assignee.id === currentUserId);
                            const canApproveItem =
                                canManageClose ||
                                (item.approver?.id != null && item.approver.id === currentUserId);

                            return (
                                <div
                                    key={item.templateItemId}
                                    className="rounded-lg border border-white/10 bg-white/[0.03] p-4"
                                >
                                    <div className="flex flex-col gap-4 xl:flex-row xl:items-start xl:justify-between">
                                        <div className="space-y-2">
                                            <div className="flex flex-wrap items-center gap-3">
                                                <p className="text-sm font-semibold text-white">
                                                    {item.sortOrder}. {item.label}
                                                </p>
                                                <span
                                                    className={[
                                                        "rounded-full px-2.5 py-1 text-[11px] font-medium",
                                                        item.satisfied
                                                            ? "border border-emerald-400/30 bg-emerald-300/10 text-emerald-100"
                                                            : item.completed
                                                              ? "border border-sky-400/30 bg-sky-300/10 text-sky-100"
                                                              : "border border-amber-400/30 bg-amber-300/10 text-amber-100",
                                                    ].join(" ")}
                                                >
                                                    {item.satisfied ? "Satisfied" : item.completed ? "Awaiting approval" : "Open"}
                                                </span>
                                            </div>
                                            <p className="text-sm leading-6 text-zinc-400">{item.guidance}</p>
                                            <p className="text-xs text-zinc-500">
                                                {item.completed
                                                    ? `Completed${item.completedBy ? ` by ${item.completedBy.fullName}` : ""}.`
                                                    : "Not completed yet."}{" "}
                                                {item.approved
                                                    ? `Approved${item.approvedBy ? ` by ${item.approvedBy.fullName}` : ""}.`
                                                    : item.approver
                                                      ? "Approval still pending."
                                                      : "No approver required yet."}
                                            </p>
                                        </div>
                                        <div className="grid gap-3 sm:grid-cols-2 xl:w-[28rem]">
                                            <label className="space-y-2 text-xs text-zinc-400">
                                                <span>Assignee</span>
                                                <select
                                                    value={item.assignee?.id ?? ""}
                                                    disabled={!canManageClose}
                                                    onChange={(event) =>
                                                        void handleAssignPlaybookItem(
                                                            item.templateItemId,
                                                            event.target.value || null,
                                                            item.approver?.id ?? null
                                                        )
                                                    }
                                                    className="w-full rounded-md border border-zinc-800 bg-black px-3 py-2 text-sm text-white"
                                                >
                                                    <option value="">Unassigned</option>
                                                    {memberships.map((membership) => (
                                                        <option key={`assignee-${membership.id}`} value={membership.user.id}>
                                                            {membership.user.fullName}
                                                        </option>
                                                    ))}
                                                </select>
                                            </label>
                                            <label className="space-y-2 text-xs text-zinc-400">
                                                <span>Approver</span>
                                                <select
                                                    value={item.approver?.id ?? ""}
                                                    disabled={!canManageClose}
                                                    onChange={(event) =>
                                                        void handleAssignPlaybookItem(
                                                            item.templateItemId,
                                                            item.assignee?.id ?? null,
                                                            event.target.value || null
                                                        )
                                                    }
                                                    className="w-full rounded-md border border-zinc-800 bg-black px-3 py-2 text-sm text-white"
                                                >
                                                    <option value="">No approver</option>
                                                    {memberships.map((membership) => (
                                                        <option key={`approver-${membership.id}`} value={membership.user.id}>
                                                            {membership.user.fullName}
                                                        </option>
                                                    ))}
                                                </select>
                                            </label>
                                            <button
                                                type="button"
                                                disabled={!canCompleteItem}
                                                onClick={() => void handleCompletePlaybookItem(item.templateItemId, !item.completed)}
                                                className="rounded-md border border-white/10 px-3 py-2 text-sm text-zinc-100 hover:bg-white/[0.05] disabled:opacity-50"
                                            >
                                                {item.completed ? "Reopen item" : "Mark complete"}
                                            </button>
                                            <button
                                                type="button"
                                                disabled={!item.completed || !canApproveItem}
                                                onClick={() => void handleApprovePlaybookItem(item.templateItemId, !item.approved)}
                                                className="rounded-md bg-emerald-300 px-3 py-2 text-sm font-medium text-black disabled:opacity-50"
                                            >
                                                {item.approved ? "Clear approval" : "Approve item"}
                                            </button>
                                        </div>
                                    </div>
                                </div>
                            );
                        })
                    )}
                </div>
            </SectionBand>

            <SectionBand
                eyebrow="Month attestation"
                title="Who owns the month and what it means to close it"
                description="Capture the accountable owner, the reviewing approver, and the short certification summary that says the month is ready to move from active work into formal approval."
            >
                <div className="grid gap-6 xl:grid-cols-[0.9fr_1.1fr]">
                    <div>
                        {!canManageClose ? (
                            <div className="mb-4">
                                <StatusBanner
                                    tone="muted"
                                    title="Attestation access is limited"
                                    message="Only workspace owners and admins can update the month-end owner, approver, and certification summary."
                                />
                            </div>
                        ) : null}
                        {attestationError ? (
                            <div className="mb-4">
                                <StatusBanner tone="error" title="Attestation not saved" message={attestationError} />
                            </div>
                        ) : null}
                        {attestationMessage ? (
                            <div className="mb-4">
                                <StatusBanner tone="success" title="Attestation updated" message={attestationMessage} />
                            </div>
                        ) : null}
                        <form className="space-y-3" onSubmit={handleSaveAttestation}>
                            <div className="grid gap-3 sm:grid-cols-2">
                                <label className="space-y-2 text-sm text-zinc-300">
                                    <span>Close owner</span>
                                    <select
                                        value={attestationOwnerUserId}
                                        disabled={!canManageClose}
                                        onChange={(event) => setAttestationOwnerUserId(event.target.value)}
                                        className="w-full rounded-md border border-zinc-700 bg-zinc-950 px-3 py-2 text-white outline-none"
                                    >
                                        <option value="">Select owner</option>
                                        {memberships.map((membership) => (
                                            <option key={`attestation-owner-${membership.id}`} value={membership.user.id}>
                                                {membership.user.fullName}
                                            </option>
                                        ))}
                                    </select>
                                </label>
                                <label className="space-y-2 text-sm text-zinc-300">
                                    <span>Approver</span>
                                    <select
                                        value={attestationApproverUserId}
                                        disabled={!canManageClose}
                                        onChange={(event) => setAttestationApproverUserId(event.target.value)}
                                        className="w-full rounded-md border border-zinc-700 bg-zinc-950 px-3 py-2 text-white outline-none"
                                    >
                                        <option value="">Select approver</option>
                                        {memberships.map((membership) => (
                                            <option key={`attestation-approver-${membership.id}`} value={membership.user.id}>
                                                {membership.user.fullName}
                                            </option>
                                        ))}
                                    </select>
                                </label>
                            </div>
                            <label className="space-y-2 text-sm text-zinc-300">
                                <span>Attestation summary for {selectedMonth || "this month"}</span>
                                <textarea
                                    value={attestationSummary}
                                    onChange={(event) => setAttestationSummary(event.target.value)}
                                    rows={5}
                                    disabled={!canManageClose}
                                    className="w-full rounded-md border border-zinc-700 bg-zinc-950 px-3 py-2 text-white outline-none"
                                    placeholder="This month is materially complete, unresolved items are documented, and leadership understands the remaining judgment calls."
                                />
                            </label>
                            <div className="flex flex-wrap gap-3">
                                <button
                                    type="submit"
                                    disabled={!canManageClose || savingAttestation}
                                    className="rounded-md border border-white/10 px-4 py-2.5 text-sm font-semibold text-zinc-100 hover:bg-white/[0.05] disabled:opacity-50"
                                >
                                    {savingAttestation ? "Saving attestation..." : "Save attestation plan"}
                                </button>
                                <button
                                    type="button"
                                    disabled={!canManageClose || !attestationReady || confirmingAttestation}
                                    onClick={() => void handleConfirmAttestation()}
                                    className="rounded-md bg-emerald-300 px-4 py-2.5 text-sm font-semibold text-black disabled:opacity-50"
                                >
                                    {confirmingAttestation ? "Confirming..." : "Confirm month attestation"}
                                </button>
                            </div>
                        </form>
                    </div>
                    <div className="space-y-3">
                        <div className="rounded-lg border border-white/10 bg-white/[0.03] p-4">
                            <p className="text-xs uppercase tracking-[0.16em] text-zinc-500">
                                Current accountability
                            </p>
                            <div className="mt-4 grid gap-3 sm:grid-cols-2">
                                <div className="rounded-md border border-white/6 bg-black/20 p-3">
                                    <p className="text-xs uppercase tracking-[0.14em] text-zinc-500">Close owner</p>
                                    <p className="mt-2 text-sm font-medium text-white">
                                        {closeAttestation?.closeOwner?.fullName ?? "Not assigned yet"}
                                    </p>
                                </div>
                                <div className="rounded-md border border-white/6 bg-black/20 p-3">
                                    <p className="text-xs uppercase tracking-[0.14em] text-zinc-500">Approver</p>
                                    <p className="mt-2 text-sm font-medium text-white">
                                        {closeAttestation?.closeApprover?.fullName ?? "Not assigned yet"}
                                    </p>
                                </div>
                            </div>
                            <div className="mt-4 rounded-md border border-white/6 bg-black/20 p-3">
                                <p className="text-xs uppercase tracking-[0.14em] text-zinc-500">Certification summary</p>
                                <p className="mt-2 text-sm leading-6 text-zinc-200">
                                    {closeAttestation?.summary?.trim() ||
                                        "No month-level attestation summary has been recorded yet."}
                                </p>
                            </div>
                        </div>
                        {closeAttestation?.attested ? (
                            <StatusBanner
                                tone="success"
                                title="Attestation confirmed"
                                message={`Confirmed${closeAttestation.attestedBy ? ` by ${closeAttestation.attestedBy.fullName}` : ""}${closeAttestation.attestedAt ? ` on ${new Date(closeAttestation.attestedAt).toLocaleString("en-US", { month: "short", day: "numeric", hour: "numeric", minute: "2-digit" })}` : ""}.`}
                            />
                        ) : (
                            <StatusBanner
                                tone="muted"
                                title="Attestation still pending"
                                message="Save the owner, approver, and certification summary first, then confirm the month-level attestation when the team is ready to stand behind the close story."
                            />
                        )}
                    </div>
                </div>
            </SectionBand>

            <SectionBand
                eyebrow="Approvals"
                title="Close sign-off"
                description="Record who approved the month-end story and why it was ready to hand off. This is the difference between finished work and trusted work."
            >
                <div className="grid gap-6 xl:grid-cols-[0.9fr_1.1fr]">
                    <div>
                        {!canManageClose ? (
                            <div className="mb-4">
                                <StatusBanner
                                    tone="muted"
                                    title="Approval access is limited"
                                    message="Only workspace owners and admins can record a formal month-end sign-off."
                                />
                            </div>
                        ) : null}
                        {signoffError ? (
                            <div className="mb-4">
                                <StatusBanner tone="error" title="Sign-off not saved" message={signoffError} />
                            </div>
                        ) : null}
                        {signoffMessage ? (
                            <div className="mb-4">
                                <StatusBanner tone="success" title="Sign-off recorded" message={signoffMessage} />
                            </div>
                        ) : null}
                        {currentUserHasSignedOff ? (
                            <div className="mb-4">
                                <StatusBanner
                                    tone="success"
                                    title="You have already signed off"
                                    message="Your approval is already part of this month's close history."
                                />
                            </div>
                        ) : null}
                        <form className="space-y-3" onSubmit={handleAddCloseSignoff}>
                            <label className="space-y-2 text-sm text-zinc-300">
                                <span>Approval summary for {selectedMonth || "this month"}</span>
                                <textarea
                                    value={signoffSummary}
                                    onChange={(event) => setSignoffSummary(event.target.value)}
                                    rows={4}
                                    disabled={!canManageClose || currentUserHasSignedOff}
                                    className="w-full rounded-md border border-zinc-700 bg-zinc-950 px-3 py-2 text-white outline-none"
                                    placeholder="Close reviewed, reconciliations cleared, adjustments approved, and no unresolved material exceptions remain."
                                />
                            </label>
                            <button
                                type="submit"
                                disabled={!canManageClose || currentUserHasSignedOff || savingSignoff}
                                className="rounded-md bg-emerald-300 px-4 py-2.5 text-sm font-semibold text-black disabled:opacity-50"
                            >
                                {savingSignoff ? "Recording sign-off..." : "Approve close"}
                            </button>
                        </form>
                    </div>
                    <div className="space-y-3">
                        {closeSignoffs.length === 0 ? (
                            <StatusBanner
                                tone="muted"
                                title="No sign-offs recorded yet"
                                message="Once leadership or finance owners approve the close, that approval trail will appear here."
                            />
                        ) : (
                            closeSignoffs.map((signoff) => (
                                <div
                                    key={signoff.id}
                                    className="rounded-lg border border-white/10 bg-white/[0.03] p-4"
                                >
                                    <div className="flex flex-wrap items-center justify-between gap-3">
                                        <p className="text-xs uppercase tracking-[0.16em] text-zinc-500">
                                            Period close signed off
                                        </p>
                                        <p className="text-xs text-zinc-500">
                                            {new Date(signoff.createdAt).toLocaleString("en-US", {
                                                month: "short",
                                                day: "numeric",
                                                hour: "numeric",
                                                minute: "2-digit",
                                            })}
                                        </p>
                                    </div>
                                    <p className="mt-3 text-sm leading-6 text-zinc-200">{signoff.details}</p>
                                </div>
                            ))
                        )}
                    </div>
                </div>
            </SectionBand>

            <SectionBand
                eyebrow="Adjustment history"
                title={`Posted adjustments for ${selectedMonth || "the selected month"}`}
                description="Review what has already been posted before you add more. This keeps duplicate accruals and forgotten reversals from sneaking in."
            >
                <div className="space-y-3">
                    {monthAdjustments.length === 0 ? (
                        <StatusBanner
                            tone="muted"
                            title="No manual adjustments posted yet"
                            message="Once month-end entries are posted, they will appear here with their reasons and account impact."
                        />
                    ) : (
                        monthAdjustments.map((entry) => (
                            <div
                                key={entry.journalEntryId}
                                className="rounded-lg border border-white/10 bg-white/[0.03] p-4"
                            >
                                <div className="flex flex-wrap items-center justify-between gap-3">
                                    <div>
                                        <p className="text-sm font-semibold text-white">{entry.description}</p>
                                        <p className="mt-1 text-sm text-zinc-400">
                                            {entry.entryDate}
                                            {entry.adjustmentReason ? ` · ${entry.adjustmentReason}` : ""}
                                        </p>
                                    </div>
                                    <button
                                        type="button"
                                        onClick={() =>
                                            applySavedTemplate({
                                                id: entry.journalEntryId,
                                                name: entry.description,
                                                description: entry.description,
                                                adjustmentReason: entry.adjustmentReason ?? "",
                                                lines: entry.lines.map((line) => ({
                                                    accountCode: line.accountCode,
                                                    accountName: line.accountName,
                                                    entrySide: line.entrySide,
                                                    amount: Number(line.amount).toFixed(2),
                                                })),
                                                savedAt: entry.createdAt,
                                            })
                                        }
                                        className="rounded-md border border-white/10 px-3 py-2 text-sm text-zinc-200 hover:bg-white/[0.05]"
                                    >
                                        Reuse as template
                                    </button>
                                </div>
                                <div className="mt-3 flex flex-wrap gap-2">
                                    {entry.lines.map((line, index) => (
                                        <span
                                            key={`${entry.journalEntryId}-${index}`}
                                            className="rounded-full border border-white/10 bg-black/20 px-3 py-1 text-xs text-zinc-300"
                                        >
                                            {line.accountCode} · {line.entrySide} · $
                                            {Number(line.amount).toFixed(2)}
                                        </span>
                                    ))}
                                </div>
                            </div>
                        ))
                    )}
                </div>
            </SectionBand>
        </main>
    );
}

export default function ClosePage() {
    return (
        <Suspense
            fallback={
                <LoadingPanel
                    title="Loading close workspace."
                    message="Gathering checklist items, ledger activity, and period controls into one place."
                />
            }
        >
            <ClosePageContent />
        </Suspense>
    );
}
