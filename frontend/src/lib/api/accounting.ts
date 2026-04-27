import { apiFetch } from "./client";

export type LedgerAccountReference = {
    accountCode: string;
    accountName: string;
    classification: string;
    sourceKinds: string[];
    categoryHints: string[];
    activityEntryCount: number;
    lastEntryDate: string | null;
    debitTotal: number;
    creditTotal: number;
};

export async function listLedgerAccounts(
    organizationId: string
): Promise<LedgerAccountReference[]> {
    return apiFetch<LedgerAccountReference[]>(
        `/api/ledger/accounts?organizationId=${encodeURIComponent(organizationId)}`,
        { method: "GET" }
    );
}
