import { apiFetch } from "./client";
import { NotificationSummaryItem } from "./auth";

export type WorkflowAttentionTask = {
    taskId: string;
    transactionId: string | null;
    notificationId: string | null;
    taskType: string;
    priority: string;
    overdue: boolean;
    title: string;
    description: string;
    dueDate: string | null;
    assignedToUserId: string | null;
    assignedToUserName: string | null;
    merchant: string | null;
    amount: number | null;
    transactionDate: string | null;
    proposedCategory: string | null;
    confidenceScore: number | null;
    route: string | null;
    actionPath: string | null;
    resolutionComment: string | null;
    acknowledgedByUserId: string | null;
    acknowledgedAt: string | null;
    snoozedUntil: string | null;
    resolvedByUserId: string | null;
    resolvedAt: string | null;
};

export type WorkflowInboxSummary = {
    cardId: string;
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

export async function getWorkflowInbox(
    organizationId: string
): Promise<WorkflowInboxSummary> {
    const query = new URLSearchParams({ organizationId });
    return apiFetch<WorkflowInboxSummary>(
        `/api/workflows/inbox?${query.toString()}`,
        { method: "GET" }
    );
}

export async function acknowledgeWorkflowAttentionTask(
    organizationId: string,
    taskId: string,
    note: string
): Promise<WorkflowAttentionTask> {
    const query = new URLSearchParams({ organizationId });
    return apiFetch<WorkflowAttentionTask>(
        `/api/workflows/inbox/attention-tasks/${encodeURIComponent(taskId)}/acknowledge?${query.toString()}`,
        {
            method: "POST",
            body: JSON.stringify({ note }),
        }
    );
}

export async function resolveWorkflowAttentionTask(
    organizationId: string,
    taskId: string,
    note: string
): Promise<void> {
    const query = new URLSearchParams({ organizationId });
    await apiFetch<void>(
        `/api/workflows/inbox/attention-tasks/${encodeURIComponent(taskId)}/resolve?${query.toString()}`,
        {
            method: "POST",
            body: JSON.stringify({ note }),
        }
    );
}

export async function listWorkflowNotifications(
    organizationId: string
): Promise<NotificationSummaryItem[]> {
    const query = new URLSearchParams({ organizationId });
    return apiFetch<NotificationSummaryItem[]>(
        `/api/workflows/notifications?${query.toString()}`,
        { method: "GET" }
    );
}

export async function listAttentionNotifications(
    organizationId: string
): Promise<NotificationSummaryItem[]> {
    const query = new URLSearchParams({ organizationId });
    return apiFetch<NotificationSummaryItem[]>(
        `/api/workflows/notifications/attention?${query.toString()}`,
        { method: "GET" }
    );
}

export async function listDeadLetterNotifications(
    organizationId: string
): Promise<NotificationSummaryItem[]> {
    const query = new URLSearchParams({ organizationId });
    return apiFetch<NotificationSummaryItem[]>(
        `/api/workflows/notifications/dead-letter?${query.toString()}`,
        { method: "GET" }
    );
}

export async function listResolvedDeadLetterNotifications(
    organizationId: string
): Promise<NotificationSummaryItem[]> {
    const query = new URLSearchParams({ organizationId });
    return apiFetch<NotificationSummaryItem[]>(
        `/api/workflows/notifications/dead-letter/history?${query.toString()}`,
        { method: "GET" }
    );
}

export async function retryDeadLetterNotification(
    organizationId: string,
    notificationId: string,
    note: string
): Promise<NotificationSummaryItem> {
    const query = new URLSearchParams({ organizationId });
    return apiFetch<NotificationSummaryItem>(
        `/api/workflows/notifications/${notificationId}/dead-letter/retry?${query.toString()}`,
        {
            method: "POST",
            body: JSON.stringify({ note }),
        }
    );
}

export async function resolveDeadLetterNotification(
    organizationId: string,
    notificationId: string,
    note: string
): Promise<NotificationSummaryItem> {
    const query = new URLSearchParams({ organizationId });
    return apiFetch<NotificationSummaryItem>(
        `/api/workflows/notifications/${notificationId}/dead-letter/resolve?${query.toString()}`,
        {
            method: "POST",
            body: JSON.stringify({ note }),
        }
    );
}

export async function requeueFailedNotification(
    organizationId: string,
    notificationId: string
): Promise<NotificationSummaryItem> {
    const query = new URLSearchParams({ organizationId });
    return apiFetch<NotificationSummaryItem>(
        `/api/workflows/notifications/${notificationId}/requeue?${query.toString()}`,
        {
            method: "POST",
        }
    );
}
