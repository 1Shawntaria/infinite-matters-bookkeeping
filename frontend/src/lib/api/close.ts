import { apiFetch } from "./client";
import { AuditEventSummary } from "./audit";

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

export type ClosePlaybookItem = {
    id: string | null;
    templateItemId: string;
    month: string | null;
    label: string;
    guidance: string;
    sortOrder: number;
    assignee: {
        id: string;
        email: string;
        fullName: string;
    } | null;
    approver: {
        id: string;
        email: string;
        fullName: string;
    } | null;
    completedAt: string | null;
    completedBy: {
        id: string;
        email: string;
        fullName: string;
    } | null;
    approvedAt: string | null;
    approvedBy: {
        id: string;
        email: string;
        fullName: string;
    } | null;
    createdAt: string;
    completed: boolean;
    approved: boolean;
    satisfied: boolean;
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

export async function listCloseNotes(
    organizationId: string,
    month: string
): Promise<AuditEventSummary[]> {
    const query = new URLSearchParams({ organizationId, month });
    return apiFetch<AuditEventSummary[]>(`/api/periods/notes?${query.toString()}`, {
        method: "GET",
    });
}

export async function addCloseNote(
    organizationId: string,
    month: string,
    note: string
): Promise<AuditEventSummary> {
    return apiFetch<AuditEventSummary>(
        `/api/periods/notes?organizationId=${encodeURIComponent(organizationId)}`,
        {
            method: "POST",
            body: JSON.stringify({ month, note }),
        }
    );
}

export async function listCloseSignoffs(
    organizationId: string,
    month: string
): Promise<AuditEventSummary[]> {
    const query = new URLSearchParams({ organizationId, month });
    return apiFetch<AuditEventSummary[]>(`/api/periods/signoffs?${query.toString()}`, {
        method: "GET",
    });
}

export async function addCloseSignoff(
    organizationId: string,
    month: string,
    summary: string
): Promise<AuditEventSummary> {
    return apiFetch<AuditEventSummary>(
        `/api/periods/signoffs?organizationId=${encodeURIComponent(organizationId)}`,
        {
            method: "POST",
            body: JSON.stringify({ month, note: summary }),
        }
    );
}

export async function listClosePlaybookItems(
    organizationId: string,
    month: string
): Promise<ClosePlaybookItem[]> {
    const query = new URLSearchParams({ organizationId, month });
    return apiFetch<ClosePlaybookItem[]>(`/api/periods/playbook?${query.toString()}`, {
        method: "GET",
    });
}

export async function assignClosePlaybookItem(
    organizationId: string,
    templateItemId: string,
    payload: {
        month: string;
        assigneeUserId: string | null;
        approverUserId: string | null;
    }
): Promise<ClosePlaybookItem> {
    const query = new URLSearchParams({ organizationId });
    return apiFetch<ClosePlaybookItem>(
        `/api/periods/playbook/${templateItemId}/assignment?${query.toString()}`,
        {
            method: "POST",
            body: JSON.stringify(payload),
        }
    );
}

export async function completeClosePlaybookItem(
    organizationId: string,
    templateItemId: string,
    month: string,
    marked: boolean
): Promise<ClosePlaybookItem> {
    const query = new URLSearchParams({ organizationId });
    return apiFetch<ClosePlaybookItem>(
        `/api/periods/playbook/${templateItemId}/complete?${query.toString()}`,
        {
            method: "POST",
            body: JSON.stringify({ month, marked }),
        }
    );
}

export async function approveClosePlaybookItem(
    organizationId: string,
    templateItemId: string,
    month: string,
    marked: boolean
): Promise<ClosePlaybookItem> {
    const query = new URLSearchParams({ organizationId });
    return apiFetch<ClosePlaybookItem>(
        `/api/periods/playbook/${templateItemId}/approve?${query.toString()}`,
        {
            method: "POST",
            body: JSON.stringify({ month, marked }),
        }
    );
}
