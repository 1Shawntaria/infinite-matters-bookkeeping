"use client";

import { useEffect, useState } from "react";

const ACCESS_TOKEN_KEY = "accessToken";
const ORGANIZATION_ID_KEY = "organizationId";
const ORGANIZATION_ID_CHANGED_EVENT = "organization-id-changed";

function browserStorage() {
    if (typeof window === "undefined") return null;
    return window.sessionStorage;
}

function legacyStorage() {
    if (typeof window === "undefined") return null;
    return window.localStorage;
}

export function clearLegacyAccessToken() {
    legacyStorage()?.removeItem(ACCESS_TOKEN_KEY);
    browserStorage()?.removeItem(ACCESS_TOKEN_KEY);
}

export function getOrganizationId(): string {
    return browserStorage()?.getItem(ORGANIZATION_ID_KEY)
        ?? legacyStorage()?.getItem(ORGANIZATION_ID_KEY)
        ?? "";
}

export function useOrganizationId() {
    const [organizationId, setOrganizationIdState] = useState("");

    useEffect(() => {
        const syncOrganizationId = () => {
            setOrganizationIdState(getOrganizationId());
        };

        syncOrganizationId();
        window.addEventListener(ORGANIZATION_ID_CHANGED_EVENT, syncOrganizationId);

        return () => {
            window.removeEventListener(ORGANIZATION_ID_CHANGED_EVENT, syncOrganizationId);
        };
    }, []);

    return organizationId;
}

export function storeAuthSession(organizationId: string) {
    const storage = browserStorage();
    if (!storage) return;

    clearLegacyAccessToken();
    setOrganizationId(organizationId);

    legacyStorage()?.removeItem(ORGANIZATION_ID_KEY);
}

export function setOrganizationId(organizationId: string) {
    const storage = browserStorage();
    if (!storage) return;

    storage.setItem(ORGANIZATION_ID_KEY, organizationId);
    window.dispatchEvent(new Event(ORGANIZATION_ID_CHANGED_EVENT));
}

export function clearAuthSession() {
    clearLegacyAccessToken();
    browserStorage()?.removeItem(ORGANIZATION_ID_KEY);
    legacyStorage()?.removeItem(ORGANIZATION_ID_KEY);
    window.dispatchEvent(new Event(ORGANIZATION_ID_CHANGED_EVENT));
}

export function redirectToLogin() {
    if (typeof window === "undefined") return;
    if (window.location.pathname !== "/login") {
        window.location.assign("/login");
    }
}
