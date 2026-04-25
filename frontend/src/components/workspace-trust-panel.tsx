"use client";

import { useMemo } from "react";
import { useQuery } from "@tanstack/react-query";
import {
    AuthSessionSummary,
    CurrentUser,
    getCurrentUser,
    listAuthSessions,
    listOrganizations,
    OrganizationSummary,
} from "@/lib/api/auth";
import { useOrganizationSession } from "@/lib/auth/session";

function formatTimestamp(value?: string | null) {
    if (!value) return "No recent activity";

    return new Date(value).toLocaleString("en-US", {
        month: "short",
        day: "numeric",
        hour: "numeric",
        minute: "2-digit",
    });
}

export function WorkspaceTrustPanel() {
    const { organizationId, hydrated } = useOrganizationSession();
    const userQuery = useQuery<CurrentUser, Error>({
        queryKey: ["currentUser"],
        enabled: hydrated,
        queryFn: () => getCurrentUser(),
    });
    const sessionsQuery = useQuery<AuthSessionSummary[], Error>({
        queryKey: ["authSessions"],
        enabled: hydrated,
        queryFn: () => listAuthSessions(),
    });
    const organizationsQuery = useQuery<OrganizationSummary[], Error>({
        queryKey: ["organizations"],
        enabled: hydrated,
        queryFn: () => listOrganizations(),
    });

    const currentOrganization = useMemo(
        () =>
            (organizationsQuery.data ?? []).find((organization) => organization.id === organizationId) ??
            null,
        [organizationId, organizationsQuery.data]
    );
    const activeSessions = (sessionsQuery.data ?? []).filter((session) => session.active);
    const latestSession = activeSessions[0] ?? sessionsQuery.data?.[0] ?? null;

    return (
        <div className="rounded-lg border border-white/10 bg-white/[0.03] p-4">
            <p className="text-xs uppercase tracking-[0.18em] text-zinc-500">Session and trust</p>
            <div className="mt-3 space-y-4">
                <div>
                    <p className="text-sm font-semibold text-white">
                        {userQuery.data?.fullName ?? "Loading user..."}
                    </p>
                    <p className="mt-1 text-xs text-zinc-400">
                        {userQuery.data?.email ?? "Checking signed-in identity"}
                    </p>
                </div>

                <div className="rounded-md border border-white/10 bg-black/20 px-3 py-3">
                    <p className="text-xs uppercase tracking-[0.18em] text-zinc-500">Current workspace</p>
                    <p className="mt-2 text-sm font-medium text-white">
                        {currentOrganization?.name ?? "Loading workspace..."}
                    </p>
                    <p className="mt-1 text-xs text-zinc-400">
                        {currentOrganization
                            ? `${currentOrganization.planTier} · ${currentOrganization.timezone}`
                            : "Plan tier and timezone appear here once the workspace loads."}
                    </p>
                </div>

                <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-1 xl:grid-cols-2">
                    <div className="rounded-md border border-white/10 bg-black/20 px-3 py-3">
                        <p className="text-xs uppercase tracking-[0.18em] text-zinc-500">Active sessions</p>
                        <p className="mt-2 text-lg font-semibold text-white">
                            {sessionsQuery.data ? activeSessions.length : "-"}
                        </p>
                        <p className="mt-1 text-xs text-zinc-400">
                            {latestSession
                                ? `Last seen ${formatTimestamp(latestSession.lastUsedAt ?? latestSession.createdAt)}`
                                : "Session details load here for quick trust checks."}
                        </p>
                    </div>
                    <div className="rounded-md border border-white/10 bg-black/20 px-3 py-3">
                        <p className="text-xs uppercase tracking-[0.18em] text-zinc-500">Workspace mode</p>
                        <p className="mt-2 text-lg font-semibold text-white">
                            {currentOrganization?.planTier ?? "-"}
                        </p>
                        <p className="mt-1 text-xs text-zinc-400">
                            {currentOrganization
                                ? "Membership, timezone, and session context are aligned."
                                : "Checking workspace context..."}
                        </p>
                    </div>
                </div>
            </div>
        </div>
    );
}
