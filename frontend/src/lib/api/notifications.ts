import { apiFetch } from "./client";
import { NotificationSummaryItem } from "./auth";

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
