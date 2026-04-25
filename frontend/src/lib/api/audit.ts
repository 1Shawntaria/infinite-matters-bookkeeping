import { apiFetch } from "./client";

export type AuditEventSummary = {
    id: string;
    organizationId: string;
    actorUserId: string | null;
    eventType: string;
    entityType: string;
    entityId: string;
    details: string;
    createdAt: string;
};

export async function listAuditEvents(organizationId: string): Promise<AuditEventSummary[]> {
    const query = new URLSearchParams({ organizationId });
    return apiFetch<AuditEventSummary[]>(`/api/audit/events?${query.toString()}`, {
        method: "GET",
    });
}
