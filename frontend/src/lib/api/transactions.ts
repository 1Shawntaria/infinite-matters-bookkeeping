import { apiFetch } from "./client";
import { ImportedTransactionSummary } from "./imports";

export async function listTransactions(
    organizationId: string
): Promise<ImportedTransactionSummary[]> {
    return apiFetch<ImportedTransactionSummary[]>(
        `/api/transactions?organizationId=${encodeURIComponent(organizationId)}`,
        {
            method: "GET",
        }
    );
}
