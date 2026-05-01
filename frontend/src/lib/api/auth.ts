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
    invitationTtlDays: number;
    closeMaterialityThreshold: number;
    minimumCloseNotesRequired: number;
    requireSignoffBeforeClose: boolean;
    minimumSignoffCount: number;
    requireOwnerSignoffBeforeClose: boolean;
    requireTemplateCompletionBeforeClose: boolean;
    role: string | null;
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
    closeControlDisposition: string | null;
    closeControlNextTouchOn: string | null;
    closeControlResolutionNote: string | null;
    closeControlResolvedAt: string | null;
    closeControlResolvedByUserId: string | null;
    scheduledFor: string | null;
    lastAttemptedAt: string | null;
    sentAt: string | null;
    createdAt: string;
};

export type InvitationAcceptRequest = {
    fullName?: string;
    password?: string;
};

export type InvitationPreview = {
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

export async function revokeAuthSession(sessionId: string, reason: string): Promise<AuthSessionSummary> {
    return apiFetch<AuthSessionSummary>(`/api/auth/sessions/${sessionId}/revoke`, {
        method: "POST",
        body: JSON.stringify({ reason }),
        includeOrganizationId: false,
    });
}

export async function listAuthActivity(): Promise<AuthActivityItem[]> {
    return apiFetch<AuthActivityItem[]>("/api/auth/activity", {
        method: "GET",
        includeOrganizationId: false,
    });
}

export async function listAuthNotifications(): Promise<NotificationSummaryItem[]> {
    return apiFetch<NotificationSummaryItem[]>("/api/auth/notifications", {
        method: "GET",
        includeOrganizationId: false,
    });
}

export async function getInvitationPreview(token: string): Promise<InvitationPreview> {
    return apiFetch<InvitationPreview>(`/api/auth/invitations/${token}`, {
        method: "GET",
        includeAuth: false,
        includeOrganizationId: false,
    });
}

export async function acceptInvitation(
    token: string,
    payload?: InvitationAcceptRequest
): Promise<LoginResponse> {
    return apiFetch<LoginResponse>(`/api/auth/invitations/${token}/accept`, {
        method: "POST",
        body: JSON.stringify(payload ?? {}),
        includeAuth: false,
        includeOrganizationId: false,
    });
}
