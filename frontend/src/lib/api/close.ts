import { apiFetch } from "./client";

export type AccountingPeriodSummary = {
    id: string;
    periodStart: string;
    periodEnd: string;
    status: "OPEN" | "CLOSED";
    closeMethod: "CHECKLIST" | "OVERRIDE" | null;
    overrideReason: string | null;
    overrideApprovedByUserId: string | null;
    closedAt: string | null;
    createdAt: string;
};

export type CloseChecklistItem = {
    itemType: string;
    label: string;
    complete: boolean;
    detail: string;
};

export type CloseChecklistSummary = {
    periodStart: string;
    periodEnd: string;
    closeReady: boolean;
    items: CloseChecklistItem[];
};

export type LedgerLineSummary = {
    accountCode: string;
    accountName: string;
    entrySide: "DEBIT" | "CREDIT";
    amount: number;
};

export type LedgerEntrySummary = {
    journalEntryId: string;
    transactionId: string | null;
    entryDate: string;
    description: string;
    entryType: string;
    adjustmentReason: string | null;
    createdAt: string;
    lines: LedgerLineSummary[];
};

export type CreateAdjustmentEntryRequest = {
    entryDate: string;
    description: string;
    adjustmentReason: string;
    lines: Array<{
        accountCode: string;
        accountName: string;
        entrySide: "DEBIT" | "CREDIT";
        amount: number;
    }>;
};

export async function listAccountingPeriods(
    organizationId: string
): Promise<AccountingPeriodSummary[]> {
    return apiFetch<AccountingPeriodSummary[]>(
        `/api/periods?organizationId=${encodeURIComponent(organizationId)}`,
        { method: "GET" }
    );
}

export async function getCloseChecklist(
    organizationId: string,
    month: string
): Promise<CloseChecklistSummary> {
    const query = new URLSearchParams({ organizationId, month });
    return apiFetch<CloseChecklistSummary>(`/api/periods/checklist?${query.toString()}`, {
        method: "GET",
    });
}

export async function closePeriod(organizationId: string, month: string) {
    return apiFetch<AccountingPeriodSummary>(
        `/api/periods/close?organizationId=${encodeURIComponent(organizationId)}`,
        {
            method: "POST",
            body: JSON.stringify({ month }),
        }
    );
}

export async function forceClosePeriod(
    organizationId: string,
    month: string,
    reason: string
) {
    return apiFetch<AccountingPeriodSummary>(
        `/api/periods/force-close?organizationId=${encodeURIComponent(organizationId)}`,
        {
            method: "POST",
            body: JSON.stringify({ month, reason }),
        }
    );
}

export async function listLedgerEntries(
    organizationId: string
): Promise<LedgerEntrySummary[]> {
    return apiFetch<LedgerEntrySummary[]>(
        `/api/ledger/entries?organizationId=${encodeURIComponent(organizationId)}`,
        { method: "GET" }
    );
}

export async function createAdjustmentEntry(
    organizationId: string,
    payload: CreateAdjustmentEntryRequest
) {
    return apiFetch<LedgerEntrySummary>(
        `/api/adjustments?organizationId=${encodeURIComponent(organizationId)}`,
        {
            method: "POST",
            body: JSON.stringify(payload),
        }
    );
}
