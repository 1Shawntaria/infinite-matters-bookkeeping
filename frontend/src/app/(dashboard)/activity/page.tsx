"use client";

import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { Suspense, useMemo, useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { LoadingPanel, PageHero, SectionBand, StatusBanner, SummaryMetric } from "@/components/app-surfaces";
import { useOrganizationSession } from "@/lib/auth/session";
import {
    AuthActivityItem,
    AuthSessionSummary,
    listAuthActivity,
    listAuthSessions,
    revokeAuthSession,
} from "@/lib/api/auth";
import { AuditEventSummary, listAuditEvents } from "@/lib/api/audit";
import { getDashboardSnapshot } from "@/lib/api/dashboard";
import { ImportedTransactionHistoryItem, listImportHistory } from "@/lib/api/imports";
import {
    buildAuditCloseControlFollowUp,
    FollowUpAction,
    getCloseControlEvents,
} from "@/lib/close-follow-up";

type TimelineFilter = "ALL" | "SECURITY" | "IMPORT" | "ACCESS" | "AUDIT";

type TimelineEntry = {
    id: string;
    lane: TimelineFilter | "NOTIFICATION";
    title: string;
    detail: string;
    timestamp: string;
    helper: string;
    entityId: string;
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
        entityId: item.transactionId,
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
        entityId: item.entityId,
    };
}

function auditEntry(item: AuditEventSummary): TimelineEntry {
    const accessEvent =
        item.entityType === "organization_invitation" ||
        item.entityType === "organization_membership";
    return {
        id: `audit-${item.id}`,
        lane: accessEvent ? "ACCESS" : "AUDIT",
        title: formatEventLabel(item.eventType),
        detail: item.details,
        timestamp: item.createdAt,
        helper: `${formatEventLabel(item.entityType)} · ${item.entityId}`,
        entityId: item.entityId,
    };
}

function ActivityPageContent() {
    const searchParams = useSearchParams();
    const initialLane = searchParams.get("lane");
    const initialEntityId = searchParams.get("entityId") ?? "";
    const initialSearch = searchParams.get("search") ?? "";
    const focusLabel = searchParams.get("label") ?? "";
    const { organizationId, hydrated } = useOrganizationSession();
    const [filter, setFilter] = useState<TimelineFilter>(
        initialLane === "SECURITY" ||
            initialLane === "IMPORT" ||
            initialLane === "ACCESS" ||
            initialLane === "AUDIT"
            ? initialLane
            : "ALL"
    );
    const [search, setSearch] = useState(initialSearch || initialEntityId);
    const [revokingSessionId, setRevokingSessionId] = useState<string | null>(null);
    const [sessionMessage, setSessionMessage] = useState("");
    const [sessionError, setSessionError] = useState("");
    const queryClient = useQueryClient();

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
    const dashboardQuery = useQuery({
        queryKey: ["dashboardSnapshot", organizationId],
        enabled: hydrated && Boolean(organizationId),
        queryFn: () => getDashboardSnapshot(organizationId),
    });

    const loading =
        hydrated && organizationId
            ? authSessionsQuery.isLoading ||
              authActivityQuery.isLoading ||
              importHistoryQuery.isLoading ||
              auditEventsQuery.isLoading ||
              dashboardQuery.isLoading
            : false;
    const queryError =
        authSessionsQuery.error?.message ??
        authActivityQuery.error?.message ??
        importHistoryQuery.error?.message ??
        auditEventsQuery.error?.message ??
        dashboardQuery.error?.message ??
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
    const closeControlEvents = useMemo(
        () => getCloseControlEvents(auditEventsQuery.data ?? []),
        [auditEventsQuery.data]
    );
    const attestationUpdatedCount = closeControlEvents.filter(
        (item) => item.eventType === "PERIOD_CLOSE_ATTESTATION_UPDATED"
    ).length;
    const attestationConfirmedCount = closeControlEvents.filter(
        (item) => item.eventType === "PERIOD_CLOSE_ATTESTED"
    ).length;
    const checklistCloseCount = closeControlEvents.filter(
        (item) => item.eventType === "PERIOD_CLOSED"
    ).length;
    const forceCloseCount = closeControlEvents.filter(
        (item) => item.eventType === "PERIOD_FORCE_CLOSED"
    ).length;
    const recentCloseControlEvent = closeControlEvents[0] ?? null;
    const closeControlHealthTitle =
        forceCloseCount > 0
            ? "Force-close activity needs review"
            : attestationUpdatedCount > attestationConfirmedCount
              ? "Attestation follow-through is lagging"
              : closeControlEvents.length > 0
                ? "Recent close controls look healthy"
                : "No close control history yet";
    const closeControlHealthMessage =
        forceCloseCount > 0
            ? `${forceCloseCount} recent force-close event(s) were recorded. Review whether the month-end control path is being bypassed too often.`
            : attestationUpdatedCount > attestationConfirmedCount
              ? `${attestationUpdatedCount - attestationConfirmedCount} attestation plan update(s) still do not have a matching confirmation in recent history.`
              : closeControlEvents.length > 0
                ? "Recent close history shows attestation and closure events landing without obvious override pressure."
                : "Once close attestation and close actions happen, this panel will summarize how clean that control sequence has been.";
    const closeControlFollowUp: FollowUpAction | null = useMemo(
        () =>
            buildAuditCloseControlFollowUp(
                closeControlEvents,
                dashboardQuery.data?.workflowInbox.attentionTasks ?? []
            ),
        [closeControlEvents, dashboardQuery.data?.workflowInbox.attentionTasks]
    );

    const visibleEntries = useMemo(() => {
        const normalizedSearch = search.trim().toLowerCase();
        return allEntries.filter((entry) => {
            const matchesFilter = filter === "ALL" || entry.lane === filter;
            const matchesEntity =
                !initialEntityId || entry.entityId === initialEntityId;
            const matchesSearch =
                normalizedSearch.length === 0 ||
                entry.title.toLowerCase().includes(normalizedSearch) ||
                entry.detail.toLowerCase().includes(normalizedSearch) ||
                entry.helper.toLowerCase().includes(normalizedSearch) ||
                entry.entityId.toLowerCase().includes(normalizedSearch);
            return matchesFilter && matchesEntity && matchesSearch;
        });
    }, [allEntries, filter, initialEntityId, search]);
    const activeSessions = (authSessionsQuery.data ?? []).filter((session) => session.active);
    const latestSession = activeSessions[0] ?? authSessionsQuery.data?.[0] ?? null;

    async function handleRevokeSession(sessionId: string) {
        setSessionError("");
        setSessionMessage("");
        setRevokingSessionId(sessionId);

        try {
            await revokeAuthSession(sessionId, "Revoked from security activity workspace");
            await Promise.all([
                queryClient.invalidateQueries({ queryKey: ["authSessions"] }),
                queryClient.invalidateQueries({ queryKey: ["authActivity"] }),
            ]);
            setSessionMessage("Session revoked. The activity feed has been refreshed.");
        } catch (error) {
            setSessionError(
                error instanceof Error ? error.message : "Unable to revoke the selected session."
            );
        } finally {
            setRevokingSessionId(null);
        }
    }

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
                    label="Access events"
                    value={`${(auditEventsQuery.data ?? []).filter((item) => item.entityType === "organization_invitation" || item.entityType === "organization_membership").length}`}
                    detail="Invitations, membership changes, and workspace access actions."
                />
                <SummaryMetric
                    label="Most recent event"
                    value={allEntries[0] ? formatTimestamp(allEntries[0].timestamp) : "Quiet"}
                    detail={allEntries[0]?.title ?? "No recent activity yet."}
                    tone={allEntries.length > 0 ? "success" : "default"}
                />
            </div>

            {sessionError ? (
                <StatusBanner
                    tone="error"
                    title="Session action failed"
                    message={sessionError}
                />
            ) : null}

            {sessionMessage ? (
                <StatusBanner
                    tone="success"
                    title="Session updated"
                    message={sessionMessage}
                />
            ) : null}

            {initialEntityId ? (
                <StatusBanner
                    tone="muted"
                    title="Focused activity trace"
                    message={
                        focusLabel
                            ? `Showing activity related to ${focusLabel}. Clear the filter controls to widen the operational picture.`
                            : `Showing activity for record ${initialEntityId}. Clear the filter controls to widen the operational picture.`
                    }
                />
            ) : null}

            <SectionBand
                eyebrow="Close controls"
                title="Attestation control quality"
                description="Use this to spot whether month-end routing, attestation follow-through, and force-close exceptions are trending in a healthy direction."
            >
                <div className="grid gap-4 xl:grid-cols-[1.1fr_0.9fr]">
                    <div className="grid gap-4 md:grid-cols-2">
                        <SummaryMetric
                            label="Attestation plans updated"
                            value={`${attestationUpdatedCount}`}
                            detail="Recent month-level routing or summary updates captured in audit history."
                        />
                        <SummaryMetric
                            label="Attestations confirmed"
                            value={`${attestationConfirmedCount}`}
                            detail="Recent month-level confirmations completed by the assigned approver."
                            tone={attestationConfirmedCount >= attestationUpdatedCount ? "success" : "warning"}
                        />
                        <SummaryMetric
                            label="Checklist closes"
                            value={`${checklistCloseCount}`}
                            detail="Months closed through the standard checklist-driven path."
                            tone={checklistCloseCount > 0 ? "success" : "default"}
                        />
                        <SummaryMetric
                            label="Force closes"
                            value={`${forceCloseCount}`}
                            detail="Months that needed an override rather than the standard close flow."
                            tone={forceCloseCount === 0 ? "success" : "warning"}
                        />
                    </div>
                    <div className="space-y-4">
                        <StatusBanner
                            tone={forceCloseCount > 0 ? "error" : attestationUpdatedCount > attestationConfirmedCount ? "muted" : "success"}
                            title={closeControlHealthTitle}
                            message={closeControlHealthMessage}
                        />
                        <div className="rounded-lg border border-white/10 bg-white/[0.03] p-4">
                            <h3 className="text-sm font-semibold text-white">Recent close control signal</h3>
                            <div className="mt-3 space-y-3 text-sm text-zinc-400">
                                <p>
                                    {recentCloseControlEvent
                                        ? `${formatEventLabel(recentCloseControlEvent.eventType)} was the latest close-control event at ${formatTimestamp(recentCloseControlEvent.createdAt)}.`
                                        : "No close-control audit events have been recorded yet for this workspace."}
                                </p>
                                <p>
                                    {forceCloseCount > 0
                                        ? "Treat overrides as exceptions worth explaining, not just mechanics worth recording."
                                        : "A healthy trail shows attestation updates turning into confirmations before standard close happens."}
                                </p>
                            </div>
                        </div>
                        {closeControlFollowUp ? (
                            <div className="rounded-lg border border-emerald-400/20 bg-emerald-300/10 p-4">
                                <h3 className="text-sm font-semibold text-white">{closeControlFollowUp.title}</h3>
                                <p className="mt-3 text-sm text-zinc-200">{closeControlFollowUp.message}</p>
                                <div className="mt-4 flex flex-wrap gap-3">
                                    <Link
                                        href={closeControlFollowUp.primaryHref}
                                        className="rounded-md bg-emerald-300 px-4 py-2.5 text-sm font-semibold text-black hover:bg-emerald-200"
                                    >
                                        {closeControlFollowUp.primaryLabel}
                                    </Link>
                                    <Link
                                        href={closeControlFollowUp.secondaryHref}
                                        className="rounded-md border border-white/10 px-4 py-2.5 text-sm text-zinc-100 hover:bg-white/[0.05]"
                                    >
                                        {closeControlFollowUp.secondaryLabel}
                                    </Link>
                                </div>
                            </div>
                        ) : null}
                    </div>
                </div>
            </SectionBand>

            <SectionBand
                eyebrow="Session controls"
                title="Manage signed-in sessions"
                description="Use this view to spot long-lived sessions, revoke anything suspicious, and keep access aligned with the team you expect."
            >
                <div className="grid gap-4 xl:grid-cols-[1.15fr_0.85fr]">
                    <div className="space-y-3">
                        {(authSessionsQuery.data ?? []).length > 0 ? (
                            (authSessionsQuery.data ?? []).map((session, index) => {
                                const active = session.active;
                                const label =
                                    index === 0 && active
                                        ? "Latest active session"
                                        : active
                                          ? "Active session"
                                          : "Revoked or expired";

                                return (
                                    <div
                                        key={session.sessionId}
                                        className="rounded-lg border border-white/10 bg-white/[0.03] px-4 py-4"
                                    >
                                        <div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
                                            <div className="space-y-1">
                                                <div className="flex flex-wrap items-center gap-2">
                                                    <p className="text-sm font-semibold text-white">{label}</p>
                                                    <span className="rounded-full border border-white/10 bg-white/[0.04] px-2 py-1 text-[11px] uppercase tracking-[0.14em] text-zinc-300">
                                                        {active ? "ACTIVE" : session.revokedAt ? "REVOKED" : "EXPIRED"}
                                                    </span>
                                                </div>
                                                <p className="text-sm text-zinc-400">
                                                    Created {formatTimestamp(session.createdAt)}
                                                </p>
                                                <p className="text-xs text-zinc-500">
                                                    Last used {formatTimestamp(session.lastUsedAt ?? session.createdAt)}
                                                    {" · "}
                                                    Expires {formatTimestamp(session.expiresAt)}
                                                </p>
                                                {session.revokedReason ? (
                                                    <p className="text-xs text-zinc-500">
                                                        Reason: {session.revokedReason}
                                                    </p>
                                                ) : null}
                                            </div>

                                            {active ? (
                                                <button
                                                    type="button"
                                                    onClick={() => handleRevokeSession(session.sessionId)}
                                                    disabled={revokingSessionId === session.sessionId}
                                                    className="rounded-md border border-rose-400/30 bg-rose-500/10 px-3 py-2 text-sm text-rose-100 hover:bg-rose-500/20 disabled:opacity-50"
                                                >
                                                    {revokingSessionId === session.sessionId ? "Revoking..." : "Revoke session"}
                                                </button>
                                            ) : null}
                                        </div>
                                    </div>
                                );
                            })
                        ) : (
                            <StatusBanner
                                tone="muted"
                                title="No session history yet"
                                message="Signed-in session inventory will appear here once authentication activity is recorded."
                            />
                        )}
                    </div>

                    <div className="space-y-4">
                        <SummaryMetric
                            label="Active sessions"
                            value={`${activeSessions.length}`}
                            detail="Keep this number small and expected."
                            tone={activeSessions.length <= 1 ? "success" : "warning"}
                        />
                        <SummaryMetric
                            label="Latest refresh"
                            value={latestSession ? formatTimestamp(latestSession.lastUsedAt ?? latestSession.createdAt) : "Quiet"}
                            detail="A quick trust check for whether someone is still using the workspace."
                        />
                        <div className="rounded-lg border border-white/10 bg-white/[0.03] p-4">
                            <h3 className="text-sm font-semibold text-white">Good security hygiene</h3>
                            <div className="mt-3 space-y-3 text-sm text-zinc-400">
                                <p>Revoke anything you do not recognize, especially after contractor or device changes.</p>
                                <p>Use the sign-out control when you finish work on shared or temporary machines.</p>
                                <p>Password resets already revoke every session, so that remains your strongest recovery move.</p>
                            </div>
                        </div>
                    </div>
                </div>
            </SectionBand>

            <SectionBand
                eyebrow="Activity feed"
                title="Recent timeline"
                description="Use these filters to focus on security checks, imports, or organization changes without losing the full sequence."
                actions={
                    <div className="flex flex-wrap gap-2">
                        {(["ALL", "SECURITY", "IMPORT", "ACCESS", "AUDIT"] as TimelineFilter[]).map((item) => (
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
                <div className="mb-4 grid gap-4 lg:grid-cols-[1fr_auto]">
                    <label className="space-y-2 text-sm text-zinc-300">
                        <span>Search timeline details</span>
                        <input
                            value={search}
                            onChange={(event) => setSearch(event.target.value)}
                            className="w-full rounded-md border border-zinc-700 bg-zinc-950 px-3 py-2 text-white outline-none"
                            placeholder="Search a transaction, event id, merchant, or explanation..."
                        />
                    </label>
                    <div className="flex items-end">
                        <button
                            type="button"
                            onClick={() => {
                                setFilter("ALL");
                                setSearch("");
                            }}
                            className="rounded-md border border-white/10 px-4 py-2.5 text-sm text-zinc-100 hover:bg-white/[0.05]"
                        >
                            Clear filters
                        </button>
                    </div>
                </div>
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

export default function ActivityPage() {
    return (
        <Suspense
            fallback={
                <LoadingPanel
                    title="Loading activity."
                    message="Pulling security context, audit history, and import movement into one workspace timeline."
                />
            }
        >
            <ActivityPageContent />
        </Suspense>
    );
}
