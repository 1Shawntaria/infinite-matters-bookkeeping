"use client";

import Link from "next/link";
import { FormEvent, useEffect, useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { LoadingPanel, PageHero, SectionBand, StatusBanner, SummaryMetric } from "@/components/app-surfaces";
import { useOrganizationSession } from "@/lib/auth/session";
import { OrganizationSummary, listOrganizations } from "@/lib/api/auth";
import { getWorkspaceSettings, updateWorkspaceSettings } from "@/lib/api/settings";

function formatRole(value: string | null | undefined) {
    if (!value) return "Unknown";
    return value.toLowerCase().replace(/\b\w/g, (match) => match.toUpperCase());
}

export default function SettingsPage() {
    const { organizationId, hydrated } = useOrganizationSession();
    const queryClient = useQueryClient();
    const [workspaceName, setWorkspaceName] = useState("");
    const [workspaceTimezone, setWorkspaceTimezone] = useState("America/Los_Angeles");
    const [invitationTtlDays, setInvitationTtlDays] = useState("7");
    const [profileMessage, setProfileMessage] = useState("");
    const [policyMessage, setPolicyMessage] = useState("");
    const [profileError, setProfileError] = useState("");
    const [policyError, setPolicyError] = useState("");
    const [isSavingProfile, setIsSavingProfile] = useState(false);
    const [isSavingPolicy, setIsSavingPolicy] = useState(false);

    const organizationsQuery = useQuery<OrganizationSummary[], Error>({
        queryKey: ["organizations"],
        enabled: hydrated,
        queryFn: () => listOrganizations(),
    });

    const settingsQuery = useQuery<OrganizationSummary, Error>({
        queryKey: ["workspaceSettings", organizationId],
        enabled: hydrated && Boolean(organizationId),
        queryFn: () => getWorkspaceSettings(organizationId),
    });

    const currentOrganization = (organizationsQuery.data ?? []).find((item) => item.id === organizationId) ?? null;
    const canManageSettings =
        currentOrganization?.role === "OWNER" || currentOrganization?.role === "ADMIN";

    useEffect(() => {
        if (settingsQuery.data) {
            setWorkspaceName(settingsQuery.data.name);
            setWorkspaceTimezone(settingsQuery.data.timezone);
            setInvitationTtlDays(String(settingsQuery.data.invitationTtlDays));
        }
    }, [settingsQuery.data]);

    const queryError = organizationsQuery.error?.message ?? settingsQuery.error?.message ?? "";

    async function handleProfileSubmit(event: FormEvent<HTMLFormElement>) {
        event.preventDefault();
        if (!organizationId) return;

        setProfileError("");
        setProfileMessage("");
        setIsSavingProfile(true);

        try {
            const updated = await updateWorkspaceSettings(organizationId, {
                name: workspaceName,
                timezone: workspaceTimezone,
            });
            await Promise.all([
                queryClient.invalidateQueries({ queryKey: ["workspaceSettings", organizationId] }),
                queryClient.invalidateQueries({ queryKey: ["organizations"] }),
            ]);
            setWorkspaceName(updated.name);
            setWorkspaceTimezone(updated.timezone);
            setProfileMessage(`Workspace profile updated for ${updated.name}.`);
        } catch (saveError) {
            setProfileError(saveError instanceof Error ? saveError.message : "Unable to save workspace settings.");
        } finally {
            setIsSavingProfile(false);
        }
    }

    async function handlePolicySubmit(event: FormEvent<HTMLFormElement>) {
        event.preventDefault();
        if (!organizationId) return;

        setPolicyError("");
        setPolicyMessage("");
        setIsSavingPolicy(true);

        try {
            const updated = await updateWorkspaceSettings(organizationId, {
                invitationTtlDays: Number(invitationTtlDays),
            });
            await Promise.all([
                queryClient.invalidateQueries({ queryKey: ["workspaceSettings", organizationId] }),
                queryClient.invalidateQueries({ queryKey: ["organizations"] }),
            ]);
            setInvitationTtlDays(String(updated.invitationTtlDays));
            setPolicyMessage(`Invitation expiry updated to ${updated.invitationTtlDays} days.`);
        } catch (saveError) {
            setPolicyError(saveError instanceof Error ? saveError.message : "Unable to save workspace settings.");
        } finally {
            setIsSavingPolicy(false);
        }
    }

    if (!hydrated || organizationsQuery.isLoading || settingsQuery.isLoading) {
        return (
            <LoadingPanel
                title="Loading workspace settings."
                message="Pulling invitation policy and workspace metadata so operators can manage onboarding safely."
            />
        );
    }

    if (!organizationId || queryError) {
        return (
            <main className="p-4 sm:p-6 lg:p-8">
                <StatusBanner
                    tone="error"
                    title="Settings unavailable"
                    message={queryError || "No organization ID found. Please sign in again."}
                />
            </main>
        );
    }

    return (
        <main className="space-y-8 p-4 sm:p-6 lg:p-8">
            <PageHero
                eyebrow="Workspace settings"
                title="Settings"
                description="Manage invitation policy and keep onboarding behavior aligned with how quickly your team expects new access to be claimed."
                aside={
                    <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-1">
                        <SummaryMetric
                            label="Your role"
                            value={formatRole(currentOrganization?.role)}
                            detail="Only owners and admins can change workspace policy."
                            tone={canManageSettings ? "success" : "default"}
                        />
                        <SummaryMetric
                            label="Invite TTL"
                            value={`${settingsQuery.data?.invitationTtlDays ?? currentOrganization?.invitationTtlDays ?? 7} days`}
                            detail="This policy is used for new invites and resend recovery."
                        />
                    </div>
                }
            >
                <div className="flex flex-wrap gap-3">
                    <Link
                        href="/access"
                        className="rounded-md border border-white/10 px-4 py-2.5 text-sm text-zinc-100 hover:bg-white/[0.05]"
                    >
                        Back to access
                    </Link>
                    <Link
                        href="/dashboard"
                        className="rounded-md bg-emerald-300 px-4 py-2.5 text-sm font-semibold text-black hover:bg-emerald-200"
                    >
                        Back to dashboard
                    </Link>
                </div>
            </PageHero>

            {profileError ? <StatusBanner tone="error" title="Profile update failed" message={profileError} /> : null}
            {profileMessage ? <StatusBanner tone="success" title="Workspace profile saved" message={profileMessage} /> : null}
            {policyError ? <StatusBanner tone="error" title="Policy update failed" message={policyError} /> : null}
            {policyMessage ? <StatusBanner tone="success" title="Invitation policy saved" message={policyMessage} /> : null}

            <div className="grid gap-4 md:grid-cols-3">
                <SummaryMetric
                    label="Workspace"
                    value={settingsQuery.data?.name ?? currentOrganization?.name ?? "Current workspace"}
                    detail={`${settingsQuery.data?.planTier ?? currentOrganization?.planTier ?? "-"} · ${settingsQuery.data?.timezone ?? currentOrganization?.timezone ?? "-"}`}
                />
                <SummaryMetric
                    label="Invite expiry"
                    value={`${settingsQuery.data?.invitationTtlDays ?? 7} days`}
                    detail="Longer windows help asynchronous onboarding. Shorter windows reduce stale links."
                />
                <SummaryMetric
                    label="Policy owner"
                    value={canManageSettings ? "Operator-managed" : "Read only"}
                    detail="Members can see workspace profile, but not change invitation policy."
                />
            </div>

            <SectionBand
                eyebrow="Workspace profile"
                title="Update workspace identity"
                description="Keep the workspace name and timezone aligned with how your team reports, receives invites, and reasons about deadlines."
            >
                {canManageSettings ? (
                    <form className="grid gap-4 lg:grid-cols-2" onSubmit={handleProfileSubmit}>
                        <label className="space-y-2">
                            <span className="text-sm text-zinc-300">Workspace name</span>
                            <input
                                type="text"
                                maxLength={120}
                                value={workspaceName}
                                onChange={(event) => setWorkspaceName(event.target.value)}
                                className="w-full rounded-md border border-zinc-800 bg-black px-3 py-2 text-sm text-white outline-none transition hover:border-zinc-700"
                                required
                            />
                            <p className="text-xs text-zinc-500">
                                This appears across access management, invitations, and operator-facing workflow screens.
                            </p>
                        </label>
                        <label className="space-y-2">
                            <span className="text-sm text-zinc-300">Timezone</span>
                            <input
                                type="text"
                                maxLength={64}
                                value={workspaceTimezone}
                                onChange={(event) => setWorkspaceTimezone(event.target.value)}
                                className="w-full rounded-md border border-zinc-800 bg-black px-3 py-2 text-sm text-white outline-none transition hover:border-zinc-700"
                                required
                            />
                            <p className="text-xs text-zinc-500">
                                Use a valid IANA zone such as <code>America/Los_Angeles</code> or <code>America/New_York</code>.
                            </p>
                        </label>
                        <div className="lg:col-span-2 flex justify-end">
                            <button
                                type="submit"
                                disabled={isSavingProfile}
                                className="rounded-md bg-emerald-300 px-4 py-2.5 text-sm font-semibold text-black hover:bg-emerald-200 disabled:opacity-50"
                            >
                                {isSavingProfile ? "Saving..." : "Save profile"}
                            </button>
                        </div>
                    </form>
                ) : (
                    <StatusBanner
                        tone="muted"
                        title="Ask an owner or admin to change workspace profile"
                        message="Members can use the workspace normally, but profile settings are restricted to operators."
                    />
                )}
            </SectionBand>

            <SectionBand
                eyebrow="Invitation policy"
                title="Control invite lifetime"
                description="Set how long invitation links remain valid before they must be resent. Resends automatically pick up this policy."
            >
                {canManageSettings ? (
                    <form className="grid gap-4 lg:grid-cols-[0.7fr_auto]" onSubmit={handlePolicySubmit}>
                        <label className="space-y-2">
                            <span className="text-sm text-zinc-300">Invitation expiry window (days)</span>
                            <input
                                type="number"
                                min={1}
                                max={30}
                                step={1}
                                value={invitationTtlDays}
                                onChange={(event) => setInvitationTtlDays(event.target.value)}
                                className="w-full rounded-md border border-zinc-800 bg-black px-3 py-2 text-sm text-white outline-none transition hover:border-zinc-700"
                                required
                            />
                            <p className="text-xs text-zinc-500">
                                Recommended range is 3 to 14 days for most teams. This workspace currently allows 1 to 30 days.
                            </p>
                        </label>
                        <div className="flex items-end">
                            <button
                                type="submit"
                                disabled={isSavingPolicy}
                                className="rounded-md bg-emerald-300 px-4 py-2.5 text-sm font-semibold text-black hover:bg-emerald-200 disabled:opacity-50"
                            >
                                {isSavingPolicy ? "Saving..." : "Save policy"}
                            </button>
                        </div>
                    </form>
                ) : (
                    <StatusBanner
                        tone="muted"
                        title="Ask an owner or admin to change invite policy"
                        message="Members can use the workspace normally, but invite expiry settings are restricted to operators."
                    />
                )}
            </SectionBand>
        </main>
    );
}
