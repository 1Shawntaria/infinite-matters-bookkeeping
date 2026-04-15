import { apiFetch } from "./client";

export type LoginRequest = {
    email: string;
    password: string;
};

export type LoginResponse = {
    accessToken: string;
};

export async function login(payload: LoginRequest): Promise<LoginResponse> {
    return apiFetch<LoginResponse>("/api/auth/token", {
        method: "POST",
        body: JSON.stringify(payload),
        includeAuth: false,
        includeOrganizationId: false,
    });
}