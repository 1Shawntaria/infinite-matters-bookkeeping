"use client";

import { useQuery } from "@tanstack/react-query";
import { useState } from "react";
import { getReviewTasks, resolveReviewTask, ReviewTask } from "@/lib/api/reviews";
import { useOrganizationSession } from "@/lib/auth/session";
import {
    LoadingPanel,
    PageHero,
    SectionBand,
    StatusBanner,
    SummaryMetric,
} from "@/components/app-surfaces";

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
    const highPriorityCount = tasks.filter((task) => task.priority === "HIGH").length;

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
        return (
            <LoadingPanel
                title="Loading review queue."
                message="Gathering transactions that still need a human decision."
            />
        );
    }

    if (!organizationId) {
        return (
            <main className="p-4 sm:p-6 lg:p-8">
                <StatusBanner
                    tone="error"
                    title="Review queue unavailable"
                    message="No organization ID found. Please sign in again."
                />
            </main>
        );
    }

    return (
        <main className="space-y-8 p-4 sm:p-6 lg:p-8">
            <PageHero
                eyebrow="Workflow attention"
                title="Review Queue"
                description="Confirm ambiguous transactions quickly so period close stays smooth and the books stay trustworthy."
                aside={
                    <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-1">
                        <SummaryMetric
                            label="Open items"
                            value={`${tasks.length}`}
                            detail="Transactions still waiting on a final category."
                            tone={tasks.length === 0 ? "success" : "warning"}
                        />
                        <SummaryMetric
                            label="High priority"
                            value={`${highPriorityCount}`}
                            detail="Start with the highest-risk categorization decisions."
                            tone={highPriorityCount > 0 ? "warning" : "default"}
                        />
                    </div>
                }
            />

            {error ? (
                <StatusBanner
                    tone="error"
                    title="Unable to resolve task"
                    message={error}
                />
            ) : queryError ? (
                <StatusBanner
                    tone="error"
                    title="Review queue unavailable"
                    message={queryError}
                />
            ) : null}

            {successMessage ? (
                <StatusBanner
                    tone="success"
                    title="Task resolved"
                    message={successMessage}
                />
            ) : null}

            {tasks.length === 0 ? (
                <SectionBand
                    eyebrow="Queue status"
                    title="No review tasks remaining"
                    description="Your current queue is clear. New ambiguous imports will appear here automatically."
                >
                    <div className="grid gap-4 md:grid-cols-2">
                        <SummaryMetric
                            label="Open items"
                            value="0"
                            detail="Nothing is currently waiting on manual categorization."
                            tone="success"
                        />
                        <SummaryMetric
                            label="Next focus"
                            value="Keep imports moving"
                            detail="The fastest win now is importing the next batch or moving to reconciliation."
                        />
                    </div>
                </SectionBand>
            ) : (
                <SectionBand
                    eyebrow="Open tasks"
                    title="Transactions that need a decision"
                    description="Resolve these in order of urgency so downstream close and reconciliation work has clean inputs."
                >
                <div className="space-y-4">
                    {tasks.map((task) => (
                        <div
                            key={task.taskId}
                            className="rounded-lg border border-white/10 bg-white/[0.03] p-5"
                        >
                            <div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
                                <div className="space-y-2">
                                    <div className="flex flex-wrap items-center gap-2">
                                        <h2 className="text-lg font-semibold text-white">
                                            {task.merchant}
                                        </h2>
                                        <span className="rounded-full border border-white/10 bg-white/[0.04] px-2 py-1 text-xs text-zinc-200">
                      {task.priority}
                    </span>
                                        {task.route ? (
                                            <span className="rounded-full border border-white/10 bg-white/[0.04] px-2 py-1 text-xs text-zinc-300">
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
                                        className="rounded-md bg-emerald-300 px-4 py-2 text-sm font-semibold text-black disabled:opacity-50"
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
                </SectionBand>
            )}
        </main>
    );
}
