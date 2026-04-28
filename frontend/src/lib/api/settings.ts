import { apiFetch } from "./client";
import { OrganizationSummary } from "./auth";

export type WorkspaceSettingsUpdate = {
    name?: string;
    timezone?: string;
    invitationTtlDays?: number;
    closeMaterialityThreshold?: number;
    minimumCloseNotesRequired?: number;
    requireSignoffBeforeClose?: boolean;
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
