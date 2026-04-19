import {
    clearAuthSession,
    getOrganizationId,
    redirectToLogin,
} from "@/lib/auth/session";

const API_BASE_URL =
    process.env.NEXT_PUBLIC_API_BASE_URL || "http://localhost:8080";

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
