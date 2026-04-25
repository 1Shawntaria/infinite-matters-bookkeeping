"use client";

import { useMemo, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import Link from "next/link";
import { LoadingPanel, PageHero, SectionBand, StatusBanner, SummaryMetric } from "@/components/app-surfaces";
import { NotificationSummaryItem, listAuthNotifications } from "@/lib/api/auth";
import { listAttentionNotifications, listWorkflowNotifications } from "@/lib/api/notifications";
import { useOrganizationSession } from "@/lib/auth/session";

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

function titleCase(value: string) {
    return value.replaceAll("_", " ").toLowerCase().replace(/\b\w/g, (match) => match.toUpperCase());
}

export default function NotificationsPage() {
    const { organizationId, hydrated } = useOrganizationSession();
    const [filter, setFilter] = useState<NotificationFilter>("ALL");

    const authNotificationsQuery = useQuery<NotificationSummaryItem[], Error>({
        queryKey: ["authNotifications"],
        enabled: hydrated,
        queryFn: () => listAuthNotifications(),
    });
    const workflowNotificationsQuery = useQuery<NotificationSummaryItem[], Error>({
        queryKey: ["workflowNotifications", organizationId],
        enabled: hydrated && Boolean(organizationId),
        queryFn: () => listWorkflowNotifications(organizationId),
    });
    const attentionNotificationsQuery = useQuery<NotificationSummaryItem[], Error>({
        queryKey: ["attentionNotifications", organizationId],
        enabled: hydrated && Boolean(organizationId),
        queryFn: () => listAttentionNotifications(organizationId),
    });

    const loading =
        hydrated && organizationId
            ? authNotificationsQuery.isLoading ||
              workflowNotificationsQuery.isLoading ||
              attentionNotificationsQuery.isLoading
            : false;
    const queryError =
        authNotificationsQuery.error?.message ??
        workflowNotificationsQuery.error?.message ??
        attentionNotificationsQuery.error?.message ??
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
                            value={`${attentionIds.size}`}
                            detail="Notifications already flagged as operationally important."
                            tone={attentionIds.size > 0 ? "warning" : "success"}
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
