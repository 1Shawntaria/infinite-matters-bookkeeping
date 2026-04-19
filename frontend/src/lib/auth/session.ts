"use client";

const ACCESS_TOKEN_KEY = "accessToken";
const ORGANIZATION_ID_KEY = "organizationId";

function browserStorage() {
    if (typeof window === "undefined") return null;
    return window.sessionStorage;
}

function legacyStorage() {
    if (typeof window === "undefined") return null;
    return window.localStorage;
}

export function getAccessToken(): string | null {
    legacyStorage()?.removeItem(ACCESS_TOKEN_KEY);
    return browserStorage()?.getItem(ACCESS_TOKEN_KEY) ?? null;
}

export function getOrganizationId(): string {
    return browserStorage()?.getItem(ORGANIZATION_ID_KEY)
        ?? legacyStorage()?.getItem(ORGANIZATION_ID_KEY)
        ?? "";
}

export function storeAuthSession(accessToken: string, organizationId: string) {
    const storage = browserStorage();
    if (!storage) return;

    storage.setItem(ACCESS_TOKEN_KEY, accessToken);
    storage.setItem(ORGANIZATION_ID_KEY, organizationId);

    legacyStorage()?.removeItem(ACCESS_TOKEN_KEY);
    legacyStorage()?.removeItem(ORGANIZATION_ID_KEY);
}

export function clearAuthSession() {
    browserStorage()?.removeItem(ACCESS_TOKEN_KEY);
    browserStorage()?.removeItem(ORGANIZATION_ID_KEY);
    legacyStorage()?.removeItem(ACCESS_TOKEN_KEY);
    legacyStorage()?.removeItem(ORGANIZATION_ID_KEY);
}

export function redirectToLogin() {
    if (typeof window === "undefined") return;
    if (window.location.pathname !== "/login") {
        window.location.assign("/login");
    }
}
