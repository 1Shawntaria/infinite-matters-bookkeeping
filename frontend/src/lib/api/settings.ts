import { apiFetch } from "./client";
import { OrganizationSummary } from "./auth";

export type CloseTemplateItem = {
    id: string;
    label: string;
    guidance: string;
    sortOrder: number;
    createdAt: string;
};

export type WorkspaceSettingsUpdate = {
    name?: string;
    timezone?: string;
    invitationTtlDays?: number;
    closeMaterialityThreshold?: number;
    minimumCloseNotesRequired?: number;
    requireSignoffBeforeClose?: boolean;
    minimumSignoffCount?: number;
    requireOwnerSignoffBeforeClose?: boolean;
    requireTemplateCompletionBeforeClose?: boolean;
};

export async function getWorkspaceSettings(organizationId: string): Promise<OrganizationSummary> {
    const query = new URLSearchParams({ organizationId });
    return apiFetch<OrganizationSummary>(`/api/organizations/settings?${query.toString()}`, {
        method: "GET",
    });
}

export async function updateWorkspaceSettings(
    organizationId: string,
    update: WorkspaceSettingsUpdate
): Promise<OrganizationSummary> {
    const query = new URLSearchParams({ organizationId });
    return apiFetch<OrganizationSummary>(`/api/organizations/settings?${query.toString()}`, {
        method: "PATCH",
        body: JSON.stringify(update),
    });
}

export async function listCloseTemplateItems(organizationId: string): Promise<CloseTemplateItem[]> {
    const query = new URLSearchParams({ organizationId });
    return apiFetch<CloseTemplateItem[]>(`/api/organizations/close-template-items?${query.toString()}`, {
        method: "GET",
    });
}

export async function createCloseTemplateItem(
    organizationId: string,
    payload: { label: string; guidance: string }
): Promise<CloseTemplateItem> {
    const query = new URLSearchParams({ organizationId });
    return apiFetch<CloseTemplateItem>(`/api/organizations/close-template-items?${query.toString()}`, {
        method: "POST",
        body: JSON.stringify(payload),
    });
}

export async function deleteCloseTemplateItem(
    organizationId: string,
    itemId: string
): Promise<void> {
    const query = new URLSearchParams({ organizationId });
    await apiFetch<void>(`/api/organizations/close-template-items/${itemId}?${query.toString()}`, {
        method: "DELETE",
    });
}
