import { apiFetch } from "./client";

export type FinancialAccount = {
    id: string;
    organizationId: string;
    name: string;
    accountType: "BANK" | "CREDIT_CARD" | "CASH" | "LOAN";
    institutionName: string | null;
    currency: string;
    active: boolean;
    createdAt: string;
};

export type CreateFinancialAccountRequest = {
    organizationId: string;
    name: string;
    accountType: FinancialAccount["accountType"];
    institutionName: string;
    currency: string;
};

export async function listFinancialAccounts(
    organizationId: string
): Promise<FinancialAccount[]> {
    return apiFetch<FinancialAccount[]>(
        `/api/accounts?organizationId=${encodeURIComponent(organizationId)}`,
        {
            method: "GET",
        }
    );
}

export async function createFinancialAccount(
    payload: CreateFinancialAccountRequest
): Promise<FinancialAccount> {
    return apiFetch<FinancialAccount>("/api/accounts", {
        method: "POST",
        body: JSON.stringify(payload),
    });
}
