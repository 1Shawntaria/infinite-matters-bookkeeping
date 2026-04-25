"use client";

import Link from "next/link";
import { useMemo, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { LoadingPanel, PageHero, SectionBand, StatusBanner, SummaryMetric } from "@/components/app-surfaces";
import { useOrganizationSession } from "@/lib/auth/session";
import { AuthActivityItem, AuthSessionSummary, listAuthActivity, listAuthSessions } from "@/lib/api/auth";
import { AuditEventSummary, listAuditEvents } from "@/lib/api/audit";
import { ImportedTransactionHistoryItem, listImportHistory } from "@/lib/api/imports";

type TimelineFilter = "ALL" | "SECURITY" | "IMPORT" | "AUDIT";

type TimelineEntry = {
    id: string;
    lane: TimelineFilter | "NOTIFICATION";
    title: string;
    detail: string;
    timestamp: string;
    helper: string;
};

function formatTimestamp(value: string) {
    return new Date(value).toLocaleString("en-US", {
        month: "short",
        day: "numeric",
        hour: "numeric",
        minute: "2-digit",
    });
}

function formatEventLabel(value: string) {
    return value.replaceAll("_", " ").toLowerCase().replace(/\b\w/g, (match) => match.toUpperCase());
}

function importEntry(item: ImportedTransactionHistoryItem): TimelineEntry {
    return {
        id: `import-${item.transactionId}`,
        lane: "IMPORT",
        title: `${item.financialAccountName}: ${item.merchant}`,
        detail: `${item.status.replaceAll("_", " ")} via ${item.route.toLowerCase()}`,
        timestamp: item.importedAt,
        helper: `${item.transactionDate} · $${Number(item.amount).toFixed(2)}`,
    };
}

function authEntry(item: AuthActivityItem): TimelineEntry {
    return {
        id: `security-${item.id}`,
        lane: "SECURITY",
        title: formatEventLabel(item.eventType),
        detail: item.details,
        timestamp: item.createdAt,
        helper: item.entityType ? formatEventLabel(item.entityType) : "Authentication",
    };
}

function auditEntry(item: AuditEventSummary): TimelineEntry {
    return {
        id: `audit-${item.id}`,
        lane: "AUDIT",
        title: formatEventLabel(item.eventType),
        detail: item.details,
        timestamp: item.createdAt,
        helper: `${formatEventLabel(item.entityType)} · ${item.entityId}`,
    };
}

export default function ActivityPage() {
    const { organizationId, hydrated } = useOrganizationSession();
    const [filter, setFilter] = useState<TimelineFilter>("ALL");

    const authSessionsQuery = useQuery<AuthSessionSummary[], Error>({
        queryKey: ["authSessions"],
        enabled: hydrated,
        queryFn: () => listAuthSessions(),
    });
    const authActivityQuery = useQuery<AuthActivityItem[], Error>({
        queryKey: ["authActivity"],
        enabled: hydrated,
        queryFn: () => listAuthActivity(),
    });
    const importHistoryQuery = useQuery<ImportedTransactionHistoryItem[], Error>({
        queryKey: ["importHistory", organizationId],
        enabled: hydrated && Boolean(organizationId),
        queryFn: () => listImportHistory(organizationId),
    });
    const auditEventsQuery = useQuery<AuditEventSummary[], Error>({
        queryKey: ["auditEvents", organizationId],
        enabled: hydrated && Boolean(organizationId),
        queryFn: () => listAuditEvents(organizationId),
    });

    const loading =
        hydrated && organizationId
            ? authSessionsQuery.isLoading ||
              authActivityQuery.isLoading ||
              importHistoryQuery.isLoading ||
              auditEventsQuery.isLoading
            : false;
    const queryError =
        authSessionsQuery.error?.message ??
        authActivityQuery.error?.message ??
        importHistoryQuery.error?.message ??
        auditEventsQuery.error?.message ??
        "";

    const allEntries = useMemo(
        () =>
            [
                ...(authActivityQuery.data ?? []).map(authEntry),
                ...(importHistoryQuery.data ?? []).map(importEntry),
                ...(auditEventsQuery.data ?? []).map(auditEntry),
            ].sort(
                (left, right) =>
                    new Date(right.timestamp).getTime() - new Date(left.timestamp).getTime()
            ),
        [auditEventsQuery.data, authActivityQuery.data, importHistoryQuery.data]
    );

    const visibleEntries =
        filter === "ALL"
            ? allEntries
            : allEntries.filter((entry) => entry.lane === filter);
    const activeSessions = (authSessionsQuery.data ?? []).filter((session) => session.active);
    const latestSession = activeSessions[0] ?? authSessionsQuery.data?.[0] ?? null;

    if (!hydrated || loading) {
        return (
            <LoadingPanel
                title="Loading activity."
                message="Pulling security context, audit history, and import movement into one workspace timeline."
            />
        );
    }

    if (!organizationId || queryError) {
        return (
            <main className="p-4 sm:p-6 lg:p-8">
                <StatusBanner
                    tone="error"
                    title="Activity unavailable"
                    message={queryError || "No organization ID found. Please sign in again."}
                />
            </main>
        );
    }

    return (
        <main className="space-y-8 p-4 sm:p-6 lg:p-8">
            <PageHero
                eyebrow="Operational trace"
                title="Activity"
                description="Track authentication events, import movement, and organization-level audit history in one place so teams can answer what changed and when."
                aside={
                    <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-1">
                        <SummaryMetric
                            label="Timeline entries"
                            value={`${allEntries.length}`}
                            detail="Recent security, import, and audit activity across this workspace."
                        />
                        <SummaryMetric
                            label="Active sessions"
                            value={`${activeSessions.length}`}
                            detail={
                                latestSession
                                    ? `Last used ${formatTimestamp(latestSession.lastUsedAt ?? latestSession.createdAt)}`
                                    : "No session activity returned."
                            }
                            tone={activeSessions.length > 0 ? "success" : "default"}
                        />
                    </div>
                }
            >
                <div className="flex flex-wrap gap-3">
                    <Link
                        href="/dashboard"
                        className="rounded-md border border-white/10 px-4 py-2.5 text-sm text-zinc-100 hover:bg-white/[0.05]"
                    >
                        Back to dashboard
                    </Link>
                    <Link
                        href="/setup"
                        className="rounded-md bg-emerald-300 px-4 py-2.5 text-sm font-semibold text-black hover:bg-emerald-200"
                    >
                        Import more activity
                    </Link>
                </div>
            </PageHero>

            <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
                <SummaryMetric
                    label="Security events"
                    value={`${authActivityQuery.data?.length ?? 0}`}
                    detail="Authentication and session activity."
                />
                <SummaryMetric
                    label="Audit events"
                    value={`${auditEventsQuery.data?.length ?? 0}`}
                    detail="Organization-scoped operational changes."
                />
                <SummaryMetric
                    label="Imported rows"
                    value={`${importHistoryQuery.data?.length ?? 0}`}
                    detail="Visible imported transactions in recent history."
                />
                <SummaryMetric
                    label="Most recent event"
                    value={allEntries[0] ? formatTimestamp(allEntries[0].timestamp) : "Quiet"}
                    detail={allEntries[0]?.title ?? "No recent activity yet."}
                    tone={allEntries.length > 0 ? "success" : "default"}
                />
            </div>

            <SectionBand
                eyebrow="Activity feed"
                title="Recent timeline"
                description="Use these filters to focus on security checks, imports, or organization changes without losing the full sequence."
                actions={
                    <div className="flex flex-wrap gap-2">
                        {(["ALL", "SECURITY", "IMPORT", "AUDIT"] as TimelineFilter[]).map((item) => (
                            <button
                                key={item}
                                onClick={() => setFilter(item)}
                                className={[
                                    "rounded-md px-3 py-2 text-sm font-medium",
                                    filter === item
                                        ? "bg-emerald-300 text-black"
                                        : "border border-white/10 text-zinc-200 hover:bg-white/[0.05]",
                                ].join(" ")}
                            >
                                {item === "ALL" ? "Everything" : formatEventLabel(item)}
                            </button>
                        ))}
                    </div>
                }
            >
                {visibleEntries.length > 0 ? (
                    <div className="space-y-3">
                        {visibleEntries.map((entry) => (
                            <div
                                key={entry.id}
                                className="rounded-lg border border-white/10 bg-white/[0.03] px-4 py-4"
                            >
                                <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
                                    <div className="space-y-1">
                                        <div className="flex flex-wrap items-center gap-2">
                                            <p className="text-sm font-semibold text-white">{entry.title}</p>
                                            <span className="rounded-full border border-white/10 bg-white/[0.04] px-2 py-1 text-[11px] uppercase tracking-[0.14em] text-zinc-300">
                                                {entry.lane}
                                            </span>
                                        </div>
                                        <p className="text-sm text-zinc-400">{entry.detail}</p>
                                        <p className="text-xs text-zinc-500">{entry.helper}</p>
                                    </div>
                                    <p className="shrink-0 text-xs text-zinc-500">
                                        {formatTimestamp(entry.timestamp)}
                                    </p>
                                </div>
                            </div>
                        ))}
                    </div>
                ) : (
                    <StatusBanner
                        tone="muted"
                        title="No events match this filter"
                        message="Try another filter or bring in a new import to generate more visible activity."
                    />
                )}
            </SectionBand>
        </main>
    );
}
