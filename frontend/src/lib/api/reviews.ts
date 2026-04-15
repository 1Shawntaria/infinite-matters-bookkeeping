import { apiFetch } from "./client";

export type ReviewTask = {
    taskId: string;
    transactionId: string;
    taskType: string;
    priority: string;
    overdue: boolean;
    title: string;
    description: string;
    dueDate: string | null;
    merchant: string;
    amount: number;
    transactionDate: string;
    proposedCategory: string | null;
    confidenceScore: number | null;
    route: string | null;
    resolutionComment: string | null;
};

export type ResolveReviewTaskRequest = {
    finalCategory: string;
    resolutionComment: string;
};

export async function getReviewTasks(
    organizationId: string
): Promise<ReviewTask[]> {
    return apiFetch<ReviewTask[]>(
        `/api/reviews/tasks?organizationId=${encodeURIComponent(organizationId)}`,
        {
            method: "GET",
        }
    );
}

export async function resolveReviewTask(
    taskId: string,
    organizationId: string,
    payload: ResolveReviewTaskRequest
): Promise<void> {
    await apiFetch<void>(
        `/api/reviews/tasks/${encodeURIComponent(
            taskId
        )}/resolve?organizationId=${encodeURIComponent(organizationId)}`,
        {
            method: "POST",
            body: JSON.stringify(payload),
        }
    );
}