import {
    clearAuthSession,
    getOrganizationId,
    redirectToLogin,
} from "@/lib/auth/session";

const API_BASE_URL =
    process.env.NEXT_PUBLIC_API_BASE_URL || "http://localhost:8080";
const CSRF_COOKIE_NAME =
    process.env.NEXT_PUBLIC_CSRF_COOKIE_NAME || "im_csrf_token";
const CSRF_HEADER_NAME = "X-CSRF-Token";
const SAFE_METHODS = new Set(["GET", "HEAD", "OPTIONS", "TRACE"]);

type ApiFetchOptions = RequestInit & {
    includeAuth?: boolean;
    includeOrganizationId?: boolean;
    includeJsonHeader?: boolean;
};

export async function apiFetch<T>(
    path: string,
    options: ApiFetchOptions = {}
): Promise<T> {
    const {
        includeAuth = true,
        includeOrganizationId = true,
        includeJsonHeader = true,
        ...requestOptions
    } = options;

    const organizationId = getOrganizationId();

    const headers = new Headers(requestOptions.headers);

    if (includeJsonHeader && !headers.has("Content-Type")) {
        headers.set("Content-Type", "application/json");
    }

    if (includeOrganizationId && organizationId) {
        headers.set("X-Organization-Id", organizationId);
    }

    if (!headers.has(CSRF_HEADER_NAME) && isUnsafeMethod(requestOptions.method)) {
        const csrfToken = getCookie(CSRF_COOKIE_NAME);
        if (csrfToken) {
            headers.set(CSRF_HEADER_NAME, csrfToken);
        }
    }

    const response = await fetch(`${API_BASE_URL}${path}`, {
        ...requestOptions,
        headers,
        credentials: "include",
    });

    if (response.status === 401 && includeAuth) {
        clearAuthSession();
        redirectToLogin();
        throw new Error("Your session expired. Please sign in again.");
    }

    if (!response.ok) {
        const text = await response.text();
        throw new Error(text || `Request failed with status ${response.status}`);
    }

    const contentType = response.headers.get("content-type") || "";
    if (contentType.includes("application/json")) {
        return response.json() as Promise<T>;
    }

    return {} as T;
}

function isUnsafeMethod(method?: string) {
    return !SAFE_METHODS.has((method ?? "GET").toUpperCase());
}

function getCookie(name: string) {
    if (typeof document === "undefined") return "";

    const prefix = `${name}=`;
    const value = document.cookie
        .split(";")
        .map((cookie) => cookie.trim())
        .find((cookie) => cookie.startsWith(prefix))
        ?.slice(prefix.length) ?? "";
    return value ? decodeURIComponent(value) : "";
}
