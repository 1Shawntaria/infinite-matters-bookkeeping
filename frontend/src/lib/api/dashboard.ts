import { apiFetch } from "./client";
import type { WorkflowAttentionTask } from "./notifications";

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
    attentionTasks: WorkflowAttentionTask[];
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

export type StaleAccountItem = {
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

export type NotificationSummaryItem = {
    id: string;
    workflowTaskId: string | null;
    userId: string | null;
    category: string;
    channel: string;
    status: string;
    deliveryState: string;
    message: string;
    referenceType: string | null;
    referenceId: string | null;
    recipientEmail: string | null;
    providerName: string | null;
    providerMessageId: string | null;
    attemptCount: number;
    lastError: string | null;
    lastFailureCode: string | null;
    deadLetterResolutionStatus: string | null;
    deadLetterResolutionReasonCode: string | null;
    deadLetterResolutionNote: string | null;
    deadLetterResolvedAt: string | null;
    deadLetterResolvedByUserId: string | null;
    closeControlAcknowledgementNote: string | null;
    closeControlAcknowledgedAt: string | null;
    closeControlAcknowledgedByUserId: string | null;
    closeControlResolutionNote: string | null;
    closeControlResolvedAt: string | null;
    closeControlResolvedByUserId: string | null;
    scheduledFor: string | null;
    lastAttemptedAt: string | null;
    sentAt: string | null;
    createdAt: string;
};

export type DashboardSnapshot = {
    focusMonth: string;
    cashBalance: number;
    postedTransactionCount: number;
    primaryAction: PrimaryAction | null;
    workflowInbox: WorkflowInbox;
    period: PeriodSummary;
    expenseCategories: ExpenseCategoryItem[];
    staleAccounts: StaleAccountItem[];
    recentNotifications: NotificationSummaryItem[];
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
