import { apiFetch } from "./client";

export type ReconciliationAccountSummary = {
    itemId: string;
    accountId: string;
    accountName: string;
    accountType: string;
    lastTransactionDate: string;
    daysSinceActivity: number;
    actionKey: string;
    actionPath: string;
    actionUrgency: string;
    actionReason: string;
};

export type ReconciliationDashboard = {
    focusMonth: string;
    period: {
        closeReady: boolean;
        unreconciledAccountCount: number;
        recommendedActionLabel: string | null;
        recommendedActionKey: string | null;
        recommendedActionPath: string | null;
        recommendedActionUrgency: string | null;
    };
    unreconciledAccounts: ReconciliationAccountSummary[];
};

export async function getReconciliationDashboard(
    organizationId: string
): Promise<ReconciliationDashboard> {
    return apiFetch<ReconciliationDashboard>(
        `/api/dashboard/snapshot?organizationId=${encodeURIComponent(organizationId)}`,
        {
            method: "GET",
        }
    );
}

export async function startReconciliation(
    organizationId: string,
    payload: {
        financialAccountId: string;
        month: string;
        openingBalance: number;
        statementEndingBalance: number;
    }
) {
    return apiFetch(
        `/api/reconciliations?organizationId=${encodeURIComponent(organizationId)}`,
        {
            method: "POST",
            body: JSON.stringify(payload),
        }
    );
}