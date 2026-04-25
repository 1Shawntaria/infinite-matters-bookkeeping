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
