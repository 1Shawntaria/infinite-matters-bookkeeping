import { apiFetch } from "./client";

export type MembershipDetail = {
    id: string;
    organizationId: string;
    user: {
        id: string;
        email: string;
        fullName: string;
        createdAt: string;
    };
    role: string;
    createdAt: string;
};

export type OrganizationInvitation = {
    id: string;
    organizationId: string;
    organizationName: string;
    email: string;
    role: string;
    status: string;
    expiresAt: string;
    acceptedAt: string | null;
    revokedAt: string | null;
    createdAt: string;
    invitedByUser: {
        id: string;
        email: string;
        fullName: string;
        createdAt: string;
    } | null;
    acceptedByUser: {
        id: string;
        email: string;
        fullName: string;
        createdAt: string;
    } | null;
    inviteUrl: string | null;
    delivery: {
        notificationId: string;
        category: string;
        channel: string;
        status: string;
        deliveryState: string;
        attemptCount: number;
        lastError: string | null;
        lastFailureCode: string | null;
        providerName: string | null;
        providerMessageId: string | null;
        scheduledFor: string | null;
        lastAttemptedAt: string | null;
        sentAt: string | null;
        createdAt: string;
    } | null;
};

export async function listMemberships(organizationId: string): Promise<MembershipDetail[]> {
    const query = new URLSearchParams({ organizationId });
    return apiFetch<MembershipDetail[]>(`/api/users/memberships?${query.toString()}`, {
        method: "GET",
    });
}

export async function addMembershipByEmail(
    organizationId: string,
    email: string,
    role: string
): Promise<MembershipDetail> {
    return apiFetch<MembershipDetail>("/api/users/memberships/by-email", {
        method: "POST",
        body: JSON.stringify({ organizationId, email, role }),
    });
}

export async function listInvitations(organizationId: string): Promise<OrganizationInvitation[]> {
    const query = new URLSearchParams({ organizationId });
    return apiFetch<OrganizationInvitation[]>(`/api/users/invitations?${query.toString()}`, {
        method: "GET",
    });
}

export async function createInvitation(
    organizationId: string,
    email: string,
    role: string
): Promise<OrganizationInvitation> {
    return apiFetch<OrganizationInvitation>("/api/users/invitations", {
        method: "POST",
        body: JSON.stringify({ organizationId, email, role }),
    });
}

export async function updateMembershipRole(
    organizationId: string,
    membershipId: string,
    role: string
): Promise<MembershipDetail> {
    const query = new URLSearchParams({ organizationId });
    return apiFetch<MembershipDetail>(
        `/api/users/memberships/${membershipId}?${query.toString()}`,
        {
            method: "PATCH",
            body: JSON.stringify({ role }),
        }
    );
}

export async function removeMembership(
    organizationId: string,
    membershipId: string
): Promise<void> {
    const query = new URLSearchParams({ organizationId });
    await apiFetch<void>(`/api/users/memberships/${membershipId}?${query.toString()}`, {
        method: "DELETE",
    });
}

export async function revokeInvitation(
    organizationId: string,
    invitationId: string
): Promise<OrganizationInvitation> {
    const query = new URLSearchParams({ organizationId });
    return apiFetch<OrganizationInvitation>(`/api/users/invitations/${invitationId}?${query.toString()}`, {
        method: "DELETE",
    });
}
