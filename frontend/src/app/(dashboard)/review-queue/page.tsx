"use client";

import { useEffect, useState } from "react";
import { getReviewTasks, resolveReviewTask, ReviewTask } from "@/lib/api/reviews";

const CATEGORY_OPTIONS = [
    "SOFTWARE",
    "TRAVEL",
    "UTILITIES",
    "MEALS",
    "OTHER",
];

type PendingSelection = Record<string, string>;

export default function ReviewQueuePage() {
    const [tasks, setTasks] = useState<ReviewTask[]>([]);
    const [selectedCategories, setSelectedCategories] = useState<PendingSelection>(
        {}
    );
    const [loading, setLoading] = useState(true);
    const [resolvingTaskId, setResolvingTaskId] = useState<string | null>(null);
    const [error, setError] = useState("");
    const [successMessage, setSuccessMessage] = useState("");

    useEffect(() => {
        const organizationId = localStorage.getItem("organizationId");

        if (!organizationId) {
            setError("No organization ID found. Please sign in again.");
            setLoading(false);
            return;
        }

        getReviewTasks(organizationId)
            .then((result) => {
                setTasks(result);

                const initialSelections: PendingSelection = {};
                result.forEach((task) => {
                    initialSelections[task.taskId] = task.proposedCategory || "OTHER";
                });
                setSelectedCategories(initialSelections);
            })
            .catch((err: Error) => setError(err.message))
            .finally(() => setLoading(false));
    }, []);

    function handleCategoryChange(taskId: string, category: string) {
        setSelectedCategories((prev) => ({
            ...prev,
            [taskId]: category,
        }));
    }

    async function handleResolve(taskId: string) {
        const organizationId = localStorage.getItem("organizationId");

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

            setTasks((prev) => prev.filter((task) => task.taskId !== taskId));
            setSuccessMessage("Task resolved successfully.");
        } catch (err) {
            const message =
                err instanceof Error ? err.message : "Unable to resolve review task.";
            setError(message);
        } finally {
            setResolvingTaskId(null);
        }
    }

    if (loading) {
        return <main className="p-6">Loading review queue...</main>;
    }

    return (
        <main className="space-y-6 p-6">
            <div>
                <h1 className="text-2xl font-semibold text-white">Review Queue</h1>
                <p className="text-sm text-zinc-400">
                    Review transactions that need human confirmation before the books are finalized.
                </p>
            </div>

            {error ? (
                <div className="rounded-md border border-red-200 bg-red-50 p-4 text-red-700">
                    {error}
                </div>
            ) : null}

            {successMessage ? (
                <div className="rounded-md border border-green-200 bg-green-50 p-4 text-green-700">
                    {successMessage}
                </div>
            ) : null}

            {tasks.length === 0 ? (
                <div className="rounded-xl border border-zinc-800 bg-zinc-900 p-6">
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
                            className="rounded-xl border border-zinc-800 bg-zinc-900 p-4"
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
                                        value={selectedCategories[task.taskId] || ""}
                                        onChange={(e) =>
                                            handleCategoryChange(task.taskId, e.target.value)
                                        }
                                        className="rounded-md border border-zinc-700 bg-black px-3 py-2 text-white outline-none"
                                    >
                                        <option value="" disabled>
                                            Select category
                                        </option>
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