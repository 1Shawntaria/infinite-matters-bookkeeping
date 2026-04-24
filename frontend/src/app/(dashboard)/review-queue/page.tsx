"use client";

import { useQuery } from "@tanstack/react-query";
import { useState } from "react";
import { getReviewTasks, resolveReviewTask, ReviewTask } from "@/lib/api/reviews";
import { useOrganizationSession } from "@/lib/auth/session";

const CATEGORY_OPTIONS = [
    "SOFTWARE",
    "TRAVEL",
    "UTILITIES",
    "MEALS",
    "OTHER",
];

type PendingSelection = Record<string, string>;

export default function ReviewQueuePage() {
    const { organizationId, hydrated } = useOrganizationSession();
    const [dismissedTaskIdsByOrganization, setDismissedTaskIdsByOrganization] = useState<
        Record<string, string[]>
    >({});
    const [selectedCategories, setSelectedCategories] = useState<PendingSelection>({});
    const [resolvingTaskId, setResolvingTaskId] = useState<string | null>(null);
    const [error, setError] = useState("");
    const [successMessage, setSuccessMessage] = useState("");
    const reviewTasksQuery = useQuery<ReviewTask[], Error>({
        queryKey: ["reviewTasks", organizationId],
        enabled: hydrated && Boolean(organizationId),
        queryFn: async () => {
            const result = await getReviewTasks(organizationId);
            setError("");
            setSuccessMessage("");
            return result;
        },
    });
    const dismissedTaskIds = organizationId
        ? (dismissedTaskIdsByOrganization[organizationId] ?? [])
        : [];
    const tasks = (reviewTasksQuery.data ?? []).filter((task) => !dismissedTaskIds.includes(task.taskId));
    const loading = hydrated && organizationId ? reviewTasksQuery.isLoading : false;
    const queryError = reviewTasksQuery.error?.message ?? "";

    function handleCategoryChange(taskId: string, category: string) {
        setSelectedCategories((prev) => ({
            ...prev,
            [taskId]: category,
        }));
    }

    async function handleResolve(taskId: string) {
        if (!organizationId) {
            setError("No organization ID found. Please sign in again.");
            return;
        }

        const finalCategory = selectedCategories[taskId];
        if (!finalCategory) {
            setError("Please select a category before resolving.");
            return;
        }

        setError("");
        setSuccessMessage("");
        setResolvingTaskId(taskId);

        try {
            await resolveReviewTask(taskId, organizationId, {
                finalCategory,
                resolutionComment: "Resolved from review queue UI",
            });

            setDismissedTaskIdsByOrganization((prev) => ({
                ...prev,
                [organizationId]: [...(prev[organizationId] ?? []), taskId],
            }));
            setSuccessMessage("Task resolved successfully.");
        } catch (err) {
            const message =
                err instanceof Error ? err.message : "Unable to resolve review task.";
            setError(message);
        } finally {
            setResolvingTaskId(null);
        }
    }

    if (!hydrated || loading) {
        return <main className="p-6">Loading review queue...</main>;
    }

    if (!organizationId) {
        return (
            <main className="p-6">
                <div className="rounded-md border border-red-200 bg-red-50 p-4 text-red-700">
                    No organization ID found. Please sign in again.
                </div>
            </main>
        );
    }

    return (
        <main className="space-y-8 p-4 sm:p-6 lg:p-8">
            <div className="rounded-xl border border-zinc-900/80 bg-black/45 p-6 backdrop-blur">
                <div className="flex flex-col gap-4 lg:flex-row lg:items-end lg:justify-between">
                    <div>
                        <p className="text-xs uppercase tracking-[0.22em] text-emerald-300">
                            Workflow attention
                        </p>
                        <h1 className="mt-2 text-3xl font-semibold text-white">Review Queue</h1>
                        <p className="mt-2 text-sm text-zinc-400">
                            Review transactions that need human confirmation before the books are finalized.
                        </p>
                    </div>

                    <div className="rounded-lg border border-zinc-800 bg-zinc-950/70 px-4 py-3">
                        <p className="text-xs uppercase tracking-[0.18em] text-zinc-500">
                            Open items
                        </p>
                        <p className="mt-2 text-2xl font-semibold text-white">{tasks.length}</p>
                    </div>
                </div>
            </div>

            {error ? (
                <div className="rounded-md border border-red-200 bg-red-50 p-4 text-red-700">
                    {error}
                </div>
            ) : queryError ? (
                <div className="rounded-md border border-red-200 bg-red-50 p-4 text-red-700">
                    {queryError}
                </div>
            ) : null}

            {successMessage ? (
                <div className="rounded-md border border-green-200 bg-green-50 p-4 text-green-700">
                    {successMessage}
                </div>
            ) : null}

            {tasks.length === 0 ? (
                <div className="rounded-xl border border-zinc-900/80 bg-black/45 p-6 backdrop-blur">
                    <p className="text-white">No review tasks remaining.</p>
                    <p className="mt-2 text-sm text-zinc-400">
                        Your current review queue is clear.
                    </p>
                </div>
            ) : (
                <div className="space-y-4">
                    {tasks.map((task) => (
                        <div
                            key={task.taskId}
                            className="rounded-xl border border-zinc-900/80 bg-black/45 p-5 backdrop-blur"
                        >
                            <div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
                                <div className="space-y-2">
                                    <div className="flex flex-wrap items-center gap-2">
                                        <h2 className="text-lg font-semibold text-white">
                                            {task.merchant}
                                        </h2>
                                        <span className="rounded-full border border-zinc-700 px-2 py-1 text-xs text-zinc-300">
                      {task.priority}
                    </span>
                                        {task.route ? (
                                            <span className="rounded-full border border-zinc-700 px-2 py-1 text-xs text-zinc-300">
                        {task.route}
                      </span>
                                        ) : null}
                                    </div>

                                    <p className="text-sm text-zinc-400">{task.description}</p>

                                    <div className="grid gap-2 text-sm text-zinc-300 md:grid-cols-2 xl:grid-cols-4">
                                        <p>
                                            <span className="text-zinc-500">Amount:</span> $
                                            {Number(task.amount ?? 0).toFixed(2)}
                                        </p>
                                        <p>
                                            <span className="text-zinc-500">Date:</span>{" "}
                                            {task.transactionDate}
                                        </p>
                                        <p>
                                            <span className="text-zinc-500">Proposed:</span>{" "}
                                            {task.proposedCategory ?? "-"}
                                        </p>
                                        <p>
                                            <span className="text-zinc-500">Confidence:</span>{" "}
                                            {task.confidenceScore != null
                                                ? `${Math.round(task.confidenceScore * 100)}%`
                                                : "-"}
                                        </p>
                                    </div>
                                </div>

                                <div className="flex min-w-[240px] flex-col gap-3">
                                    <label className="text-sm font-medium text-zinc-300">
                                        Final Category
                                    </label>

                                    <select
                                        value={selectedCategories[task.taskId] || task.proposedCategory || "OTHER"}
                                        onChange={(e) =>
                                            handleCategoryChange(task.taskId, e.target.value)
                                        }
                                        className="rounded-md border border-zinc-700 bg-black px-3 py-2 text-white outline-none"
                                    >
                                        {CATEGORY_OPTIONS.map((category) => (
                                            <option key={category} value={category}>
                                                {category}
                                            </option>
                                        ))}
                                    </select>

                                    <button
                                        onClick={() => handleResolve(task.taskId)}
                                        disabled={resolvingTaskId === task.taskId}
                                        className="rounded-md bg-white px-4 py-2 text-black disabled:opacity-50"
                                    >
                                        {resolvingTaskId === task.taskId
                                            ? "Resolving..."
                                            : "Resolve Task"}
                                    </button>
                                </div>
                            </div>
                        </div>
                    ))}
                </div>
            )}
        </main>
    );
}
