import { apiFetch } from "./client";

export type ImportedTransactionSummary = {
    transactionId: string;
    financialAccountId: string;
    financialAccountName: string;
    transactionDate: string;
    postedDate: string | null;
    amount: number;
    currency: string;
    merchant: string;
    memo: string | null;
    mcc: string | null;
    proposedCategory: string | null;
    finalCategory: string | null;
    ledgerAccountCode: string | null;
    ledgerAccountName: string | null;
    route: string;
    confidenceScore: number;
    status: string;
    sourceType: string;
    importedAt: string;
};

export type ImportBatchResult = {
    importedCount: number;
    duplicateCount: number;
    reviewRequiredCount: number;
    postedCount: number;
    transactions: ImportedTransactionSummary[];
};

export type ImportedTransactionHistoryItem = {
    transactionId: string;
    financialAccountId: string;
    financialAccountName: string;
    importedAt: string;
    transactionDate: string;
    amount: number;
    merchant: string;
    proposedCategory: string | null;
    finalCategory: string | null;
    route: string;
    confidenceScore: number;
    status: string;
};

export async function importTransactionsCsv(
    organizationId: string,
    financialAccountId: string,
    file: File
): Promise<ImportBatchResult> {
    const formData = new FormData();
    formData.append("file", file);

    return apiFetch<ImportBatchResult>(
        `/api/transactions/import/csv?organizationId=${encodeURIComponent(organizationId)}&financialAccountId=${encodeURIComponent(financialAccountId)}`,
        {
            method: "POST",
            body: formData,
            includeJsonHeader: false,
        }
    );
}

export async function listImportHistory(
    organizationId: string,
    financialAccountId?: string
): Promise<ImportedTransactionHistoryItem[]> {
    const searchParams = new URLSearchParams({ organizationId });
    if (financialAccountId) {
        searchParams.set("financialAccountId", financialAccountId);
    }

    return apiFetch<ImportedTransactionHistoryItem[]>(
        `/api/transactions/import-history?${searchParams.toString()}`,
        {
            method: "GET",
        }
    );
}
