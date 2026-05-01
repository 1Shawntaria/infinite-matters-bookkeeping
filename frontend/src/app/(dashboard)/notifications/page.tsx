"use client";

import { useMemo, useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import Link from "next/link";
import { LoadingPanel, PageHero, SectionBand, StatusBanner, SummaryMetric } from "@/components/app-surfaces";
import { listOrganizations, NotificationSummaryItem, OrganizationSummary, listAuthNotifications } from "@/lib/api/auth";
import {
    acknowledgeCloseControlEscalation,
    CloseControlDisposition,
    acknowledgeWorkflowAttentionTask,
    getWorkflowInbox,
    listAttentionNotifications,
    listDeadLetterNotifications,
    listResolvedDeadLetterNotifications,
    listWorkflowNotifications,
    resolveWorkflowAttentionTask,
    resolveCloseControlEscalation,
    requeueFailedNotification,
    resolveDeadLetterNotification,
    retryDeadLetterNotification,
    WorkflowInboxSummary,
} from "@/lib/api/notifications";
import { useOrganizationSession } from "@/lib/auth/session";
import {
    buildEscalatedCloseControlAction,
    getCloseControlNextTouchDate,
    isEscalatedCloseControlNotification,
} from "@/lib/close-follow-up";

type NotificationFilter = "ALL" | "AUTH" | "WORKFLOW" | "ATTENTION";

type NotificationEntry = NotificationSummaryItem & {
    source: NotificationFilter | "DELIVERY";
};

function formatTimestamp(value?: string | null) {
    if (!value) return "Not yet attempted";

    return new Date(value).toLocaleString("en-US", {
        month: "short",
        day: "numeric",
        hour: "numeric",
        minute: "2-digit",
    });
}

function formatDate(value?: string | null) {
    if (!value) return "No due date";

    return new Date(`${value}T00:00:00`).toLocaleDateString("en-US", {
        month: "short",
        day: "numeric",
    });
}

function formatCalendarDate(value?: string | null) {
    if (!value) return "No scheduled follow-up";

    return new Date(`${value}T00:00:00`).toLocaleDateString("en-US", {
        month: "short",
        day: "numeric",
        year: "numeric",
    });
}

function todayDate() {
    return new Date().toISOString().slice(0, 10);
}

function defaultNextTouchDate() {
    const value = new Date();
    value.setDate(value.getDate() + 1);
    return value.toISOString().slice(0, 10);
}

function addDays(value: string, days: number) {
    const date = new Date(`${value}T00:00:00`);
    date.setDate(date.getDate() + days);
    return date.toISOString().slice(0, 10);
}

function laterDate(left: string, right: string) {
    return left > right ? left : right;
}

function titleCase(value: string) {
    return value.replaceAll("_", " ").toLowerCase().replace(/\b\w/g, (match) => match.toUpperCase());
}

const DEFAULT_CLOSE_CONTROL_DISPOSITION: CloseControlDisposition = "WAITING_ON_APPROVER";

const CLOSE_CONTROL_DISPOSITION_OPTIONS: Array<{
    value: CloseControlDisposition;
    label: string;
}> = [
    { value: "WAITING_ON_APPROVER", label: "Waiting on approver" },
    { value: "OVERRIDE_DOCS_IN_PROGRESS", label: "Override docs in progress" },
    { value: "REVISIT_TOMORROW", label: "Revisit tomorrow" },
];

function normalizeCloseControlDisposition(
    disposition: string | null | undefined
): CloseControlDisposition {
    if (
        disposition === "WAITING_ON_APPROVER" ||
        disposition === "OVERRIDE_DOCS_IN_PROGRESS" ||
        disposition === "REVISIT_TOMORROW"
    ) {
        return disposition;
    }
    return DEFAULT_CLOSE_CONTROL_DISPOSITION;
}

function closeControlDispositionLabel(disposition: string | null | undefined) {
    return CLOSE_CONTROL_DISPOSITION_OPTIONS.find(
        (option) => option.value === normalizeCloseControlDisposition(disposition)
    )?.label ?? "Waiting on approver";
}

function suggestNextTouchDateForEscalation(
    disposition: CloseControlDisposition,
    notification: NotificationSummaryItem,
    workflowAttentionTasks: WorkflowInboxSummary["attentionTasks"]
) {
    if (disposition !== "REVISIT_TOMORROW") {
        return null;
    }

    const matchedTask =
        workflowAttentionTasks.find((task) => task.taskId === notification.referenceId) ?? null;
    const tomorrow = defaultNextTouchDate();
    if (!matchedTask) {
        return tomorrow;
    }
    if (matchedTask.taskType === "FORCE_CLOSE_REVIEW" && !matchedTask.overdue) {
        return laterDate(addDays(todayDate(), 2), matchedTask.dueDate ?? tomorrow);
    }
    return laterDate(tomorrow, matchedTask.dueDate ?? tomorrow);
}

function nextTouchSuggestionReason(
    disposition: CloseControlDisposition,
    notification: NotificationSummaryItem,
    workflowAttentionTasks: WorkflowInboxSummary["attentionTasks"]
) {
    if (disposition !== "REVISIT_TOMORROW") {
        return null;
    }
    const matchedTask =
        workflowAttentionTasks.find((task) => task.taskId === notification.referenceId) ?? null;
    if (!matchedTask) {
        return "Suggested from the default next review window.";
    }
    if (matchedTask.taskType === "FORCE_CLOSE_REVIEW") {
        return matchedTask.overdue
            ? "Suggested for the next review day because this override review is already overdue."
            : "Suggested with extra owner time because this is override documentation follow-through.";
    }
    return matchedTask.overdue
        ? "Suggested for the next review day because attestation follow-through is already overdue."
        : "Suggested from the current attestation due date and approver follow-through pressure.";
}

export default function NotificationsPage() {
    const { organizationId, hydrated } = useOrganizationSession();
    const [filter, setFilter] = useState<NotificationFilter>("ALL");
    const [actionMessage, setActionMessage] = useState("");
    const [actionError, setActionError] = useState("");
    const [actingNotificationId, setActingNotificationId] = useState<string | null>(null);
    const [actingWorkflowTaskId, setActingWorkflowTaskId] = useState<string | null>(null);
    const [escalationNotes, setEscalationNotes] = useState<Record<string, string>>({});
    const [escalationDispositions, setEscalationDispositions] = useState<
        Record<string, CloseControlDisposition>
    >({});
    const [escalationNextTouchDates, setEscalationNextTouchDates] = useState<Record<string, string>>(
        {}
    );
    const queryClient = useQueryClient();

    const organizationsQuery = useQuery<OrganizationSummary[], Error>({
        queryKey: ["organizations"],
        enabled: hydrated,
        queryFn: () => listOrganizations(),
    });
    const authNotificationsQuery = useQuery<NotificationSummaryItem[], Error>({
        queryKey: ["authNotifications"],
        enabled: hydrated,
        queryFn: () => listAuthNotifications(),
    });
    const currentOrganization = (organizationsQuery.data ?? []).find((item) => item.id === organizationId) ?? null;
    const isAdminOperator =
        currentOrganization?.role === "OWNER" || currentOrganization?.role === "ADMIN";
    const workflowNotificationsQuery = useQuery<NotificationSummaryItem[], Error>({
        queryKey: ["workflowNotifications", organizationId],
        enabled: hydrated && Boolean(organizationId),
        queryFn: () => listWorkflowNotifications(organizationId),
    });
    const attentionNotificationsQuery = useQuery<NotificationSummaryItem[], Error>({
        queryKey: ["attentionNotifications", organizationId],
        enabled: hydrated && Boolean(organizationId) && isAdminOperator,
        queryFn: () => listAttentionNotifications(organizationId),
    });
    const workflowInboxQuery = useQuery<WorkflowInboxSummary, Error>({
        queryKey: ["workflowInbox", organizationId],
        enabled: hydrated && Boolean(organizationId),
        queryFn: () => getWorkflowInbox(organizationId),
    });
    const deadLetterNotificationsQuery = useQuery<NotificationSummaryItem[], Error>({
        queryKey: ["deadLetterNotifications", organizationId],
        enabled: hydrated && Boolean(organizationId) && isAdminOperator,
        queryFn: () => listDeadLetterNotifications(organizationId),
    });
    const resolvedDeadLetterNotificationsQuery = useQuery<NotificationSummaryItem[], Error>({
        queryKey: ["resolvedDeadLetterNotifications", organizationId],
        enabled: hydrated && Boolean(organizationId) && isAdminOperator,
        queryFn: () => listResolvedDeadLetterNotifications(organizationId),
    });

    const loading =
        hydrated && organizationId
            ? organizationsQuery.isLoading ||
              authNotificationsQuery.isLoading ||
              workflowInboxQuery.isLoading ||
              workflowNotificationsQuery.isLoading ||
              (isAdminOperator &&
                  (attentionNotificationsQuery.isLoading ||
                      deadLetterNotificationsQuery.isLoading ||
                      resolvedDeadLetterNotificationsQuery.isLoading))
            : false;
    const queryError =
        organizationsQuery.error?.message ??
        authNotificationsQuery.error?.message ??
        workflowInboxQuery.error?.message ??
        workflowNotificationsQuery.error?.message ??
        attentionNotificationsQuery.error?.message ??
        deadLetterNotificationsQuery.error?.message ??
        resolvedDeadLetterNotificationsQuery.error?.message ??
        "";

    const mergedNotifications = useMemo<NotificationEntry[]>(
        () =>
            [
                ...(authNotificationsQuery.data ?? []).map((item) => ({ ...item, source: "AUTH" as const })),
                ...(workflowNotificationsQuery.data ?? []).map((item) => ({ ...item, source: "WORKFLOW" as const })),
            ].sort((left, right) => new Date(right.createdAt).getTime() - new Date(left.createdAt).getTime()),
        [authNotificationsQuery.data, workflowNotificationsQuery.data]
    );

    const attentionIds = new Set((attentionNotificationsQuery.data ?? []).map((item) => item.id));
    const visibleNotifications =
        filter === "ALL"
            ? mergedNotifications
            : filter === "ATTENTION"
              ? mergedNotifications.filter((item) => attentionIds.has(item.id))
              : mergedNotifications.filter((item) => item.source === filter);

    const deliveryIssues = mergedNotifications.filter(
        (item) => item.deliveryState === "FAILED" || item.deliveryState === "DEAD_LETTER"
    );
    const workflowAttentionTasks = workflowInboxQuery.data?.attentionTasks ?? [];
    const deadLetterNotifications = deadLetterNotificationsQuery.data ?? [];
    const resolvedDeadLetters = resolvedDeadLetterNotificationsQuery.data ?? [];
    const escalatedCloseControlNotifications = (attentionNotificationsQuery.data ?? []).filter(
        isEscalatedCloseControlNotification
    );

    async function refreshNotificationData() {
        await Promise.all([
            queryClient.invalidateQueries({ queryKey: ["workflowInbox", organizationId] }),
            queryClient.invalidateQueries({ queryKey: ["workflowNotifications", organizationId] }),
            queryClient.invalidateQueries({ queryKey: ["attentionNotifications", organizationId] }),
            queryClient.invalidateQueries({ queryKey: ["deadLetterNotifications", organizationId] }),
            queryClient.invalidateQueries({ queryKey: ["resolvedDeadLetterNotifications", organizationId] }),
        ]);
    }

    async function handleAcknowledgeWorkflowTask(taskId: string) {
        if (!organizationId) return;

        setActionMessage("");
        setActionError("");
        setActingWorkflowTaskId(taskId);

        try {
            await acknowledgeWorkflowAttentionTask(
                organizationId,
                taskId,
                "Reviewed from notifications workspace"
            );
            await refreshNotificationData();
            setActionMessage("Workflow follow-up marked reviewed.");
        } catch (error) {
            setActionError(
                error instanceof Error ? error.message : "Unable to mark this workflow follow-up reviewed."
            );
        } finally {
            setActingWorkflowTaskId(null);
        }
    }

    async function handleAcknowledgeEscalation(notificationId: string) {
        if (!organizationId) return;

        setActionMessage("");
        setActionError("");
        setActingNotificationId(notificationId);

        const disposition =
            escalationDispositions[notificationId] ?? DEFAULT_CLOSE_CONTROL_DISPOSITION;
        try {
            await acknowledgeCloseControlEscalation(
                organizationId,
                notificationId,
                escalationNotes[notificationId] ?? "",
                disposition,
                disposition === "REVISIT_TOMORROW"
                    ? escalationNextTouchDates[notificationId] ?? null
                    : null
            );
            await refreshNotificationData();
            setActionMessage("Escalated close-control review acknowledged.");
        } catch (error) {
            setActionError(
                error instanceof Error ? error.message : "Unable to acknowledge this escalation."
            );
        } finally {
            setActingNotificationId(null);
        }
    }

    async function handleResolveEscalation(notificationId: string) {
        if (!organizationId) return;

        setActionMessage("");
        setActionError("");
        setActingNotificationId(notificationId);

        const disposition =
            escalationDispositions[notificationId] ?? DEFAULT_CLOSE_CONTROL_DISPOSITION;
        try {
            await resolveCloseControlEscalation(
                organizationId,
                notificationId,
                escalationNotes[notificationId] ?? "",
                disposition,
                disposition === "REVISIT_TOMORROW"
                    ? escalationNextTouchDates[notificationId] ?? null
                    : null
            );
            await refreshNotificationData();
            setActionMessage("Escalated close-control review resolved.");
        } catch (error) {
            setActionError(
                error instanceof Error ? error.message : "Unable to resolve this escalation."
            );
        } finally {
            setActingNotificationId(null);
        }
    }

    async function handleResolveWorkflowTask(taskId: string) {
        if (!organizationId) return;

        setActionMessage("");
        setActionError("");
        setActingWorkflowTaskId(taskId);

        try {
            await resolveWorkflowAttentionTask(
                organizationId,
                taskId,
                "Reviewed and cleared from notifications workspace"
            );
            await refreshNotificationData();
            setActionMessage("Workflow follow-up cleared.");
        } catch (error) {
            setActionError(
                error instanceof Error ? error.message : "Unable to clear this workflow follow-up."
            );
        } finally {
            setActingWorkflowTaskId(null);
        }
    }

    async function handleRetry(notificationId: string, deadLetter: boolean) {
        if (!organizationId) return;

        setActionMessage("");
        setActionError("");
        setActingNotificationId(notificationId);

        try {
            if (deadLetter) {
                await retryDeadLetterNotification(
                    organizationId,
                    notificationId,
                    "Retried from notifications workspace"
                );
            } else {
                await requeueFailedNotification(organizationId, notificationId);
            }
            await refreshNotificationData();
            setActionMessage("Delivery retry queued successfully.");
        } catch (error) {
            setActionError(
                error instanceof Error ? error.message : "Unable to retry this notification."
            );
        } finally {
            setActingNotificationId(null);
        }
    }

    async function handleResolve(notificationId: string) {
        if (!organizationId) return;

        setActionMessage("");
        setActionError("");
        setActingNotificationId(notificationId);

        try {
            await resolveDeadLetterNotification(
                organizationId,
                notificationId,
                "Resolved from notifications workspace"
            );
            await refreshNotificationData();
            setActionMessage("Dead-letter notification marked resolved.");
        } catch (error) {
            setActionError(
                error instanceof Error ? error.message : "Unable to resolve this notification."
            );
        } finally {
            setActingNotificationId(null);
        }
    }

    if (!hydrated || loading) {
        return (
            <LoadingPanel
                title="Loading notifications."
                message="Collecting auth alerts, workflow delivery events, and attention items into one inbox."
            />
        );
    }

    if (!organizationId || queryError) {
        return (
            <main className="p-4 sm:p-6 lg:p-8">
                <StatusBanner
                    tone="error"
                    title="Notifications unavailable"
                    message={queryError || "No organization ID found. Please sign in again."}
                />
            </main>
        );
    }

    return (
        <main className="space-y-8 p-4 sm:p-6 lg:p-8">
            <PageHero
                eyebrow="Trust and delivery"
                title="Notifications"
                description="Keep security alerts, operational sends, and attention-worthy delivery issues in one place so teams can act quickly without bouncing between tools."
                aside={
                    <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-1">
                        <SummaryMetric
                            label="Inbox items"
                            value={`${mergedNotifications.length}`}
                            detail="User security and organization workflow notifications."
                        />
                        <SummaryMetric
                            label="Needs attention"
                            value={isAdminOperator ? `${attentionIds.size}` : "Member"}
                            detail={
                                isAdminOperator
                                    ? "Notifications already flagged as operationally important."
                                    : "Owner/admin delivery controls are only shown to operators with elevated workspace access."
                            }
                            tone={isAdminOperator && attentionIds.size > 0 ? "warning" : "success"}
                        />
                    </div>
                }
            >
                <div className="flex flex-wrap gap-3">
                    <Link
                        href="/activity"
                        className="rounded-md border border-white/10 px-4 py-2.5 text-sm text-zinc-100 hover:bg-white/[0.05]"
                    >
                        Open activity workspace
                    </Link>
                    <Link
                        href="/dashboard"
                        className="rounded-md bg-emerald-300 px-4 py-2.5 text-sm font-semibold text-black hover:bg-emerald-200"
                    >
                        Back to dashboard
                    </Link>
                </div>
            </PageHero>

            <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
                <SummaryMetric
                    label="Auth alerts"
                    value={`${authNotificationsQuery.data?.length ?? 0}`}
                    detail="Password reset, session, and security-related notifications."
                />
                <SummaryMetric
                    label="Workflow sends"
                    value={`${workflowNotificationsQuery.data?.length ?? 0}`}
                    detail="Organization-scoped operational delivery events."
                />
                <SummaryMetric
                    label="Delivery issues"
                    value={`${deliveryIssues.length}`}
                    detail="Failed or dead-letter notifications worth investigation."
                    tone={deliveryIssues.length > 0 ? "warning" : "success"}
                />
                <SummaryMetric
                    label="Latest delivery"
                    value={mergedNotifications[0] ? formatTimestamp(mergedNotifications[0].createdAt) : "Quiet"}
                    detail={mergedNotifications[0]?.message ?? "No recent notifications yet."}
                />
            </div>

            {escalatedCloseControlNotifications.length > 0 ? (
                <StatusBanner
                    tone="error"
                    title="Escalated close controls need owner or admin action"
                    message={`${escalatedCloseControlNotifications.length} close-control escalation(s) are now beyond routine reminders. Route those months back through the close workspace before treating the workflow as healthy again.`}
                />
            ) : null}

            <SectionBand
                eyebrow="Escalations"
                title="Escalated close controls"
                description="These are the months where routine reminders were not enough and the system is now asking an owner or admin to intervene directly."
            >
                {escalatedCloseControlNotifications.length > 0 ? (
                    <div className="space-y-3">
                        {escalatedCloseControlNotifications.map((notification) => {
                            const action = buildEscalatedCloseControlAction(
                                notification,
                                workflowAttentionTasks
                            );
                            const nextTouchDate = getCloseControlNextTouchDate(
                                notification,
                                workflowAttentionTasks
                            );
                            const busy = actingNotificationId === notification.id;
                            const reviewed = Boolean(notification.closeControlAcknowledgedAt);
                            const selectedDisposition =
                                escalationDispositions[notification.id] ??
                                normalizeCloseControlDisposition(
                                    notification.closeControlDisposition
                                );
                            const suggestedNextTouchDate = suggestNextTouchDateForEscalation(
                                selectedDisposition,
                                notification,
                                workflowAttentionTasks
                            );
                            const selectedNextTouchDate =
                                escalationNextTouchDates[notification.id] ??
                                notification.closeControlNextTouchOn ??
                                nextTouchDate ??
                                suggestedNextTouchDate ??
                                defaultNextTouchDate();
                            const suggestedNextTouchReason = nextTouchSuggestionReason(
                                selectedDisposition,
                                notification,
                                workflowAttentionTasks
                            );
                            return (
                                <div
                                    key={`escalation-${notification.id}`}
                                    className="rounded-lg border border-rose-300/30 bg-rose-300/10 px-4 py-4"
                                >
                                    <div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
                                        <div className="space-y-1">
                                            <div className="flex flex-wrap items-center gap-2">
                                                <p className="text-sm font-semibold text-white">{action.title}</p>
                                                <span className="rounded-full border border-rose-300/40 bg-rose-300/10 px-2 py-1 text-[11px] uppercase tracking-[0.14em] text-rose-100">
                                                    Escalated
                                                </span>
                                                <span className="rounded-full border border-amber-300/40 bg-amber-300/10 px-2 py-1 text-[11px] uppercase tracking-[0.14em] text-amber-100">
                                                    Owner/Admin
                                                </span>
                                                {reviewed ? (
                                                    <span className="rounded-full border border-amber-300/40 bg-amber-300/10 px-2 py-1 text-[11px] uppercase tracking-[0.14em] text-amber-50">
                                                        Reviewed
                                                    </span>
                                                ) : null}
                                            </div>
                                            <p className="text-sm text-zinc-200">{action.message}</p>
                                            <p className="text-sm text-zinc-300">{notification.message}</p>
                                            <div className="flex flex-wrap gap-x-4 gap-y-1 text-xs text-zinc-400">
                                                <span>Focus month {action.monthLabel}</span>
                                                <span>Recipient {notification.recipientEmail ?? "Unknown"}</span>
                                                <span>Sent {formatTimestamp(notification.sentAt ?? notification.createdAt)}</span>
                                            </div>
                                            {notification.closeControlAcknowledgedAt ? (
                                                <p className="text-xs text-zinc-300">
                                                    Reviewed {formatTimestamp(notification.closeControlAcknowledgedAt)}
                                                    {notification.closeControlDisposition
                                                        ? ` · ${closeControlDispositionLabel(notification.closeControlDisposition)}`
                                                        : ""}
                                                    {notification.closeControlAcknowledgementNote
                                                        ? ` · ${notification.closeControlAcknowledgementNote}`
                                                        : ""}
                                                </p>
                                            ) : null}
                                            {nextTouchDate ? (
                                                <p className="text-xs text-zinc-400">
                                                    Next touch {formatCalendarDate(nextTouchDate)}
                                                </p>
                                            ) : null}
                                            <label className="block space-y-2 pt-2">
                                                <span className="text-xs uppercase tracking-[0.14em] text-zinc-300">
                                                    Review state
                                                </span>
                                                <select
                                                    value={selectedDisposition}
                                                    onChange={(event) => {
                                                        const nextDisposition =
                                                            event.target.value as CloseControlDisposition;
                                                        setEscalationDispositions((current) => ({
                                                            ...current,
                                                            [notification.id]: nextDisposition,
                                                        }));
                                                        if (nextDisposition === "REVISIT_TOMORROW") {
                                                            setEscalationNextTouchDates((current) => ({
                                                                ...current,
                                                                [notification.id]:
                                                                    current[notification.id] ??
                                                                    notification.closeControlNextTouchOn ??
                                                                    nextTouchDate ??
                                                                    suggestNextTouchDateForEscalation(
                                                                        nextDisposition,
                                                                        notification,
                                                                        workflowAttentionTasks
                                                                    ) ??
                                                                    defaultNextTouchDate(),
                                                            }));
                                                        }
                                                    }}
                                                    className="w-full rounded-md border border-white/10 bg-black/20 px-3 py-2 text-sm text-white"
                                                >
                                                    {CLOSE_CONTROL_DISPOSITION_OPTIONS.map((option) => (
                                                        <option
                                                            key={option.value}
                                                            value={option.value}
                                                            className="bg-zinc-950"
                                                        >
                                                            {option.label}
                                                        </option>
                                                    ))}
                                                </select>
                                            </label>
                                            {selectedDisposition === "REVISIT_TOMORROW" ? (
                                                <label className="block space-y-2 pt-2">
                                                    <span className="text-xs uppercase tracking-[0.14em] text-zinc-300">
                                                        Next touch date
                                                    </span>
                                                    <input
                                                        type="date"
                                                        min={todayDate()}
                                                        value={selectedNextTouchDate}
                                                        onChange={(event) =>
                                                            setEscalationNextTouchDates((current) => ({
                                                                ...current,
                                                                [notification.id]: event.target.value,
                                                            }))
                                                        }
                                                        className="w-full rounded-md border border-white/10 bg-black/20 px-3 py-2 text-sm text-white"
                                                    />
                                                    {suggestedNextTouchDate ? (
                                                        <div className="flex flex-wrap items-center gap-3 text-xs text-zinc-400">
                                                            <span>
                                                                Suggested {formatCalendarDate(suggestedNextTouchDate)}
                                                            </span>
                                                            {suggestedNextTouchReason ? (
                                                                <span>{suggestedNextTouchReason}</span>
                                                            ) : null}
                                                            <button
                                                                type="button"
                                                                onClick={() =>
                                                                    setEscalationNextTouchDates((current) => ({
                                                                        ...current,
                                                                        [notification.id]: suggestedNextTouchDate,
                                                                    }))
                                                                }
                                                                className="rounded-md border border-white/10 px-2 py-1 text-[11px] text-zinc-200 hover:bg-white/[0.05]"
                                                            >
                                                                Use suggestion
                                                            </button>
                                                        </div>
                                                    ) : null}
                                                </label>
                                            ) : null}
                                            <label className="block space-y-2 pt-2">
                                                <span className="text-xs uppercase tracking-[0.14em] text-zinc-300">
                                                    Review note
                                                </span>
                                                <textarea
                                                    value={escalationNotes[notification.id] ?? ""}
                                                    onChange={(event) =>
                                                        setEscalationNotes((current) => ({
                                                            ...current,
                                                            [notification.id]: event.target.value,
                                                        }))
                                                    }
                                                    rows={2}
                                                    placeholder="Document what was reviewed, who owns follow-through, or why the escalation can be cleared."
                                                    className="w-full rounded-md border border-white/10 bg-black/20 px-3 py-2 text-sm text-white placeholder:text-zinc-500"
                                                />
                                            </label>
                                        </div>
                                        <div className="flex shrink-0 flex-wrap gap-2">
                                            <Link
                                                href={action.primaryHref}
                                                className="rounded-md bg-rose-200 px-3 py-2 text-sm font-semibold text-black hover:bg-rose-100"
                                            >
                                                {action.primaryLabel}
                                            </Link>
                                            <Link
                                                href={action.secondaryHref}
                                                className="rounded-md border border-white/10 px-3 py-2 text-sm text-zinc-100 hover:bg-white/[0.05]"
                                            >
                                                {action.secondaryLabel}
                                            </Link>
                                            <button
                                                type="button"
                                                onClick={() => handleAcknowledgeEscalation(notification.id)}
                                                disabled={busy || reviewed}
                                                className="rounded-md border border-white/10 px-3 py-2 text-sm text-zinc-100 hover:bg-white/[0.05] disabled:opacity-50"
                                            >
                                                {reviewed ? "Reviewed" : busy ? "Working..." : "Save review note"}
                                            </button>
                                            <button
                                                type="button"
                                                onClick={() => handleResolveEscalation(notification.id)}
                                                disabled={busy}
                                                className="rounded-md border border-rose-200/50 px-3 py-2 text-sm text-rose-50 hover:bg-rose-200/10 disabled:opacity-50"
                                            >
                                                {busy ? "Working..." : "Resolve escalation"}
                                            </button>
                                        </div>
                                    </div>
                                </div>
                            );
                        })}
                    </div>
                ) : (
                    <StatusBanner
                        tone="success"
                        title="No escalated close controls"
                        message="Routine reminders are currently enough to keep the close-control workflow moving."
                    />
                )}
            </SectionBand>

            <SectionBand
                eyebrow="Workflow inbox"
                title="Workflow follow-up"
                description="Stay on top of review queue pressure and close-control escalations without leaving the notifications workspace."
            >
                {workflowAttentionTasks.length > 0 ? (
                    <div className="space-y-3">
                        {workflowAttentionTasks.map((task) => {
                            const actionPath =
                                task.actionPath ??
                                (task.transactionId ? "/review-queue" : workflowInboxQuery.data?.recommendedActionPath) ??
                                "/dashboard";
                            const actionLabel =
                                task.taskType === "CLOSE_ATTESTATION_FOLLOW_UP"
                                    ? "Open attestation month"
                                    : task.taskType === "FORCE_CLOSE_REVIEW"
                                      ? "Review override month"
                                      : "Open workflow task";
                            const busy = actingWorkflowTaskId === task.taskId;
                            const canResolve = isAdminOperator && task.taskType === "FORCE_CLOSE_REVIEW";

                            return (
                                <div
                                    key={task.taskId}
                                    className="rounded-lg border border-white/10 bg-white/[0.03] px-4 py-4"
                                >
                                    <div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
                                        <div className="space-y-1">
                                            <div className="flex flex-wrap items-center gap-2">
                                                <p className="text-sm font-semibold text-white">{task.title}</p>
                                                <span className="rounded-full border border-white/10 bg-white/[0.04] px-2 py-1 text-[11px] uppercase tracking-[0.14em] text-zinc-300">
                                                    {titleCase(task.priority)}
                                                </span>
                                                <span className="rounded-full border border-white/10 bg-white/[0.04] px-2 py-1 text-[11px] uppercase tracking-[0.14em] text-zinc-300">
                                                    {titleCase(task.taskType)}
                                                </span>
                                                {task.overdue ? (
                                                    <span className="rounded-full border border-rose-300/40 bg-rose-300/10 px-2 py-1 text-[11px] uppercase tracking-[0.14em] text-rose-100">
                                                        Overdue
                                                    </span>
                                                ) : null}
                                                {task.acknowledgedAt ? (
                                                    <span className="rounded-full border border-amber-300/40 bg-amber-300/10 px-2 py-1 text-[11px] uppercase tracking-[0.14em] text-amber-100">
                                                        Reviewed
                                                    </span>
                                                ) : null}
                                            </div>
                                            <p className="text-sm text-zinc-300">{task.description}</p>
                                            <div className="flex flex-wrap gap-x-4 gap-y-1 text-xs text-zinc-500">
                                                <span>Due {formatDate(task.dueDate)}</span>
                                                <span>
                                                    Assigned {task.assignedToUserName ?? "Unassigned"}
                                                </span>
                                            </div>
                                            {task.acknowledgedAt ? (
                                                <p className="text-xs text-zinc-500">
                                                    Reviewed {formatTimestamp(task.acknowledgedAt)}
                                                </p>
                                            ) : null}
                                            {task.resolutionComment ? (
                                                <p className="text-xs text-zinc-500">
                                                    Audit detail: {task.resolutionComment}
                                                </p>
                                            ) : null}
                                        </div>

                                        <div className="flex shrink-0 flex-wrap gap-2">
                                            <Link
                                                href={actionPath}
                                                className="rounded-md bg-emerald-300 px-3 py-2 text-sm font-semibold text-black hover:bg-emerald-200"
                                            >
                                                {actionLabel}
                                            </Link>
                                            <button
                                                type="button"
                                                onClick={() => handleAcknowledgeWorkflowTask(task.taskId)}
                                                disabled={busy || Boolean(task.acknowledgedAt)}
                                                className="rounded-md border border-white/10 px-3 py-2 text-sm text-zinc-100 hover:bg-white/[0.05] disabled:opacity-50"
                                            >
                                                {task.acknowledgedAt ? "Reviewed" : busy ? "Working..." : "Mark reviewed"}
                                            </button>
                                            {canResolve ? (
                                                <button
                                                    type="button"
                                                    onClick={() => handleResolveWorkflowTask(task.taskId)}
                                                    disabled={busy}
                                                    className="rounded-md border border-emerald-300/40 px-3 py-2 text-sm text-emerald-100 hover:bg-emerald-300/10 disabled:opacity-50"
                                                >
                                                    {busy ? "Working..." : "Clear signal"}
                                                </button>
                                            ) : null}
                                        </div>
                                    </div>
                                </div>
                            );
                        })}
                    </div>
                ) : (
                    <StatusBanner
                        tone="success"
                        title="Workflow follow-up is under control"
                        message="No review or close-control tasks are currently bubbling up into the operational inbox."
                    />
                )}
            </SectionBand>

            {actionError ? (
                <StatusBanner
                    tone="error"
                    title="Notification action failed"
                    message={actionError}
                />
            ) : null}

            {actionMessage ? (
                <StatusBanner
                    tone="success"
                    title="Notification updated"
                    message={actionMessage}
                />
            ) : null}

            <SectionBand
                eyebrow="Delivery operations"
                title="Resolve delivery issues"
                description="Owners and admins can retry failed sends, resolve dead letters, and review what was already handled."
            >
                {isAdminOperator ? (
                    deliveryIssues.length > 0 || deadLetterNotifications.length > 0 ? (
                    <div className="space-y-3">
                        {[...deliveryIssues, ...deadLetterNotifications.filter(
                            (item) => !deliveryIssues.some((issue) => issue.id === item.id)
                        )].map((notification) => {
                            const isDeadLetter =
                                notification.deliveryState === "DEAD_LETTER" ||
                                deadLetterNotifications.some((item) => item.id === notification.id);
                            const busy = actingNotificationId === notification.id;

                            return (
                                <div
                                    key={`ops-${notification.id}`}
                                    className="rounded-lg border border-white/10 bg-white/[0.03] px-4 py-4"
                                >
                                    <div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
                                        <div className="space-y-1">
                                            <div className="flex flex-wrap items-center gap-2">
                                                <p className="text-sm font-semibold text-white">
                                                    {titleCase(notification.category)}
                                                </p>
                                                <span className="rounded-full border border-white/10 bg-white/[0.04] px-2 py-1 text-[11px] uppercase tracking-[0.14em] text-zinc-300">
                                                    {notification.deliveryState}
                                                </span>
                                                {isDeadLetter ? (
                                                    <span className="rounded-full border border-rose-300/40 bg-rose-300/10 px-2 py-1 text-[11px] uppercase tracking-[0.14em] text-rose-100">
                                                        Dead letter
                                                    </span>
                                                ) : null}
                                            </div>
                                            <p className="text-sm text-zinc-300">{notification.message}</p>
                                            <div className="flex flex-wrap gap-x-4 gap-y-1 text-xs text-zinc-500">
                                                <span>Recipient {notification.recipientEmail ?? "Unknown"}</span>
                                                <span>Attempts {notification.attemptCount}</span>
                                                <span>Last attempted {formatTimestamp(notification.lastAttemptedAt)}</span>
                                            </div>
                                            {notification.lastError ? (
                                                <p className="text-xs text-rose-200">
                                                    Last error: {notification.lastError}
                                                </p>
                                            ) : null}
                                        </div>

                                        <div className="flex flex-wrap gap-2">
                                            <button
                                                type="button"
                                                onClick={() => handleRetry(notification.id, isDeadLetter)}
                                                disabled={busy}
                                                className="rounded-md bg-emerald-300 px-3 py-2 text-sm font-semibold text-black hover:bg-emerald-200 disabled:opacity-50"
                                            >
                                                {busy ? "Working..." : "Retry delivery"}
                                            </button>
                                            {isDeadLetter ? (
                                                <button
                                                    type="button"
                                                    onClick={() => handleResolve(notification.id)}
                                                    disabled={busy}
                                                    className="rounded-md border border-white/10 px-3 py-2 text-sm text-zinc-100 hover:bg-white/[0.05] disabled:opacity-50"
                                                >
                                                    Mark resolved
                                                </button>
                                            ) : null}
                                        </div>
                                    </div>
                                </div>
                            );
                        })}
                    </div>
                    ) : (
                    <StatusBanner
                        tone="success"
                        title="Delivery pipeline looks healthy"
                        message="No failed or dead-letter workflow notifications need attention right now."
                    />
                    )
                ) : (
                    <StatusBanner
                        tone="muted"
                        title="Operator-only delivery controls"
                        message="You can still monitor notification history here, but retry and dead-letter actions are reserved for workspace owners and admins."
                    />
                )}
            </SectionBand>

            {isAdminOperator ? (
                <SectionBand
                    eyebrow="Resolved history"
                    title="Recently resolved dead letters"
                    description="This gives operators context for what was already handled so the team does not repeatedly investigate the same delivery incident."
                >
                    {resolvedDeadLetters.length > 0 ? (
                        <div className="space-y-3">
                            {resolvedDeadLetters.slice(0, 6).map((notification) => (
                                <div
                                    key={`resolved-${notification.id}`}
                                    className="rounded-lg border border-white/10 bg-white/[0.03] px-4 py-4"
                                >
                                    <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
                                        <div className="space-y-1">
                                            <div className="flex flex-wrap items-center gap-2">
                                                <p className="text-sm font-semibold text-white">
                                                    {titleCase(notification.category)}
                                                </p>
                                                <span className="rounded-full border border-white/10 bg-white/[0.04] px-2 py-1 text-[11px] uppercase tracking-[0.14em] text-zinc-300">
                                                    {notification.deadLetterResolutionStatus ?? "Resolved"}
                                                </span>
                                            </div>
                                            <p className="text-sm text-zinc-300">{notification.message}</p>
                                            <p className="text-xs text-zinc-500">
                                                Resolved {formatTimestamp(notification.deadLetterResolvedAt)}
                                                {notification.deadLetterResolutionNote
                                                    ? ` · ${notification.deadLetterResolutionNote}`
                                                    : ""}
                                            </p>
                                        </div>
                                        <p className="shrink-0 text-xs text-zinc-500">
                                            {notification.recipientEmail ?? "Unknown recipient"}
                                        </p>
                                    </div>
                                </div>
                            ))}
                        </div>
                    ) : (
                        <StatusBanner
                            tone="muted"
                            title="No resolved dead letters yet"
                            message="Resolved delivery history will appear here after operators handle failed sends."
                        />
                    )}
                </SectionBand>
            ) : null}

            <SectionBand
                eyebrow="Inbox filters"
                title="Notification inbox"
                description="Filter between personal security alerts, organization workflow sends, and the subset already flagged for attention."
                actions={
                    <div className="flex flex-wrap gap-2">
                        {(["ALL", "AUTH", "WORKFLOW", "ATTENTION"] as NotificationFilter[]).map((item) => (
                            <button
                                key={item}
                                type="button"
                                onClick={() => setFilter(item)}
                                className={[
                                    "rounded-md px-3 py-2 text-sm font-medium",
                                    filter === item
                                        ? "bg-emerald-300 text-black"
                                        : "border border-white/10 text-zinc-200 hover:bg-white/[0.05]",
                                ].join(" ")}
                            >
                                {item === "ALL" ? "Everything" : titleCase(item)}
                            </button>
                        ))}
                    </div>
                }
            >
                {visibleNotifications.length > 0 ? (
                    <div className="space-y-3">
                        {visibleNotifications.map((notification) => {
                            const flagged = attentionIds.has(notification.id);

                            return (
                                <div
                                    key={`${notification.source}-${notification.id}`}
                                    className="rounded-lg border border-white/10 bg-white/[0.03] px-4 py-4"
                                >
                                    <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
                                        <div className="space-y-1">
                                            <div className="flex flex-wrap items-center gap-2">
                                                <p className="text-sm font-semibold text-white">
                                                    {titleCase(notification.category)}
                                                </p>
                                                <span className="rounded-full border border-white/10 bg-white/[0.04] px-2 py-1 text-[11px] uppercase tracking-[0.14em] text-zinc-300">
                                                    {notification.source}
                                                </span>
                                                <span className="rounded-full border border-white/10 bg-white/[0.04] px-2 py-1 text-[11px] uppercase tracking-[0.14em] text-zinc-300">
                                                    {notification.deliveryState}
                                                </span>
                                                {flagged ? (
                                                    <span className="rounded-full border border-amber-300/40 bg-amber-300/10 px-2 py-1 text-[11px] uppercase tracking-[0.14em] text-amber-200">
                                                        Attention
                                                    </span>
                                                ) : null}
                                            </div>
                                            <p className="text-sm text-zinc-300">{notification.message}</p>
                                            <div className="flex flex-wrap gap-x-4 gap-y-1 text-xs text-zinc-500">
                                                <span>Created {formatTimestamp(notification.createdAt)}</span>
                                                <span>Channel {titleCase(notification.channel)}</span>
                                                <span>Status {titleCase(notification.status)}</span>
                                                {notification.recipientEmail ? (
                                                    <span>{notification.recipientEmail}</span>
                                                ) : null}
                                            </div>
                                            {notification.lastError ? (
                                                <p className="text-xs text-rose-200">
                                                    Last error: {notification.lastError}
                                                </p>
                                            ) : null}
                                        </div>
                                        <div className="shrink-0 text-right text-xs text-zinc-500">
                                            <p>Attempts {notification.attemptCount}</p>
                                            <p className="mt-1">
                                                Last delivery {formatTimestamp(notification.sentAt ?? notification.lastAttemptedAt)}
                                            </p>
                                        </div>
                                    </div>
                                </div>
                            );
                        })}
                    </div>
                ) : (
                    <StatusBanner
                        tone="muted"
                        title="No notifications match this filter"
                        message="Try another filter or keep using the workspace; security and workflow delivery events will appear here automatically."
                    />
                )}
            </SectionBand>
        </main>
    );
}
