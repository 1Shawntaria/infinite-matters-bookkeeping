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
    sessionStarted: boolean;
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

export type ReconciliationSession = {
    id: string;
    financialAccountId: string;
    accountName: string;
    periodStart: string;
    periodEnd: string;
    openingBalance: number;
    statementEndingBalance: number;
    computedEndingBalance: number | null;
    varianceAmount: number | null;
    notes: string | null;
    status: string;
    completedAt: string | null;
    createdAt: string;
};

export type ReconciliationTransaction = {
    transactionId: string;
    transactionDate: string;
    amount: number;
    merchant: string | null;
    memo: string | null;
    status: string;
};

export type ReconciliationAccountDetail = {
    focusMonth: string;
    financialAccountId: string;
    accountName: string;
    institutionName: string | null;
    accountType: string;
    currency: string;
    active: boolean;
    session: ReconciliationSession | null;
    bookEndingBalance: number | null;
    varianceAmount: number | null;
    postedTransactionCount: number;
    reviewRequiredCount: number;
    canStartReconciliation: boolean;
    canCompleteReconciliation: boolean;
    statusMessage: string;
    transactions: ReconciliationTransaction[];
};

export async function getReconciliationAccountDetail(
    organizationId: string,
    accountId: string,
    month?: string
): Promise<ReconciliationAccountDetail> {
    const query = new URLSearchParams({ organizationId });
    if (month) {
        query.set("month", month);
    }

    return apiFetch<ReconciliationAccountDetail>(
        `/api/reconciliations/accounts/${encodeURIComponent(accountId)}?${query.toString()}`,
        {
            method: "GET",
        }
    );
}

export async function completeReconciliation(
    organizationId: string,
    sessionId: string
) {
    return apiFetch(
        `/api/reconciliations/${encodeURIComponent(sessionId)}/complete?organizationId=${encodeURIComponent(organizationId)}`,
        {
            method: "POST",
        }
    );
}
