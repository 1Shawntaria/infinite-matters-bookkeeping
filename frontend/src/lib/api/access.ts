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
