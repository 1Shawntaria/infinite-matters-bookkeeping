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

export async function login(payload: LoginRequest): Promise<LoginResponse> {
    return apiFetch<LoginResponse>("/api/auth/token", {
        method: "POST",
        body: JSON.stringify(payload),
        includeAuth: false,
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
