import { apiFetch } from "./client";

export type LoginRequest = {
    email: string;
    password: string;
};

export type LoginResponse = {
    user?: {
        id: string;
        email: string;
        fullName: string;
    };
};

export type OrganizationSummary = {
    id: string;
    name: string;
    planTier: string;
    timezone: string;
};

export type CurrentUser = {
    id: string;
    email: string;
    fullName: string;
    createdAt: string;
};

export type AuthSessionSummary = {
    sessionId: string;
    createdAt: string;
    expiresAt: string;
    lastUsedAt: string | null;
    revokedAt: string | null;
    revokedReason: string | null;
    reuseDetectedAt: string | null;
    replacedBySessionId: string | null;
    active: boolean;
};

export type AuthActivityItem = {
    id: string;
    organizationId: string | null;
    actorUserId: string | null;
    eventType: string;
    entityType: string;
    entityId: string;
    details: string;
    createdAt: string;
};

export async function login(payload: LoginRequest): Promise<LoginResponse> {
    return apiFetch<LoginResponse>("/api/auth/token", {
        method: "POST",
        body: JSON.stringify(payload),
        includeAuth: false,
        includeOrganizationId: false,
    });
}

export async function listOrganizations(): Promise<OrganizationSummary[]> {
    return apiFetch<OrganizationSummary[]>("/api/users/organizations", {
        method: "GET",
        includeOrganizationId: false,
    });
}

export async function logout(): Promise<void> {
    await apiFetch<void>("/api/auth/logout", {
        method: "POST",
        includeAuth: false,
        includeOrganizationId: false,
    });
}

export async function getCurrentUser(): Promise<CurrentUser> {
    return apiFetch<CurrentUser>("/api/auth/me", {
        method: "GET",
        includeOrganizationId: false,
    });
}

export async function listAuthSessions(): Promise<AuthSessionSummary[]> {
    return apiFetch<AuthSessionSummary[]>("/api/auth/sessions", {
        method: "GET",
        includeOrganizationId: false,
    });
}

export async function listAuthActivity(): Promise<AuthActivityItem[]> {
    return apiFetch<AuthActivityItem[]>("/api/auth/activity", {
        method: "GET",
        includeOrganizationId: false,
    });
}
