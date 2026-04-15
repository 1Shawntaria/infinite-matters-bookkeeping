import { apiFetch } from "./client";

export type PrimaryAction = {
    cardId: string;
    label: string;
    actionKey: string;
    actionPath: string;
    itemCount: number;
    reason: string;
    urgency: string;
    source: string;
};

export type WorkflowInbox = {
    openCount: number;
    overdueCount: number;
    dueTodayCount: number;
    highPriorityCount: number;
    unassignedCount: number;
    assignedToCurrentUserCount: number;
    recommendedActionLabel: string | null;
    recommendedActionKey: string | null;
    recommendedActionPath: string | null;
    recommendedActionUrgency: string | null;
    attentionTasks: unknown[];
};

export type PeriodSummary = {
    closeReady: boolean;
    unreconciledAccountCount: number;
    recommendedActionLabel: string | null;
    recommendedActionKey: string | null;
    recommendedActionPath: string | null;
    recommendedActionUrgency: string | null;
};

export type ExpenseCategoryItem = {
    itemId: string;
    category: string;
    amount: number;
    deltaFromPreviousMonth: number;
    actionKey: string;
    actionPath: string;
    actionUrgency: string;
    actionReason: string;
};

export type DashboardSnapshot = {
    focusMonth: string;
    cashBalance: number;
    postedTransactionCount: number;
    primaryAction: PrimaryAction | null;
    workflowInbox: WorkflowInbox;
    period: PeriodSummary;
    expenseCategories: ExpenseCategoryItem[];
    staleAccounts: unknown[];
    recentNotifications: unknown[];
    [key: string]: unknown;
};

export async function getDashboardSnapshot(
    organizationId: string
): Promise<DashboardSnapshot> {
    return apiFetch<DashboardSnapshot>(
        `/api/dashboard/snapshot?organizationId=${encodeURIComponent(organizationId)}`,
        {
            method: "GET",
        }
    );
}