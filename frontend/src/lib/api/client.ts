const API_BASE_URL =
    process.env.NEXT_PUBLIC_API_BASE_URL || "http://localhost:8080";

function getAccessToken(): string | null {
    if (typeof window === "undefined") return null;
    return localStorage.getItem("accessToken");
}

function getOrganizationId(): string | null {
    if (typeof window === "undefined") return null;
    return localStorage.getItem("organizationId");
}

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

    const token = getAccessToken();
    const organizationId = getOrganizationId();

    const headers = new Headers(requestOptions.headers);

    if (includeJsonHeader && !headers.has("Content-Type")) {
        headers.set("Content-Type", "application/json");
    }

    if (includeAuth && token) {
        headers.set("Authorization", `Bearer ${token}`);
    }

    if (includeOrganizationId && organizationId) {
        headers.set("X-Organization-Id", organizationId);
    }

    const response = await fetch(`${API_BASE_URL}${path}`, {
        ...requestOptions,
        headers,
    });

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