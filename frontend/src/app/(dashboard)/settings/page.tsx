"use client";

import Link from "next/link";
import { FormEvent, useEffect, useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { LoadingPanel, PageHero, SectionBand, StatusBanner, SummaryMetric } from "@/components/app-surfaces";
import { useOrganizationSession } from "@/lib/auth/session";
import { OrganizationSummary, listOrganizations } from "@/lib/api/auth";
import {
    CloseTemplateItem,
    createCloseTemplateItem,
    deleteCloseTemplateItem,
    getWorkspaceSettings,
    listCloseTemplateItems,
    updateWorkspaceSettings,
} from "@/lib/api/settings";

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
    const [closeMaterialityThreshold, setCloseMaterialityThreshold] = useState("500");
    const [minimumCloseNotesRequired, setMinimumCloseNotesRequired] = useState("1");
    const [requireSignoffBeforeClose, setRequireSignoffBeforeClose] = useState(true);
    const [minimumSignoffCount, setMinimumSignoffCount] = useState("1");
    const [requireOwnerSignoffBeforeClose, setRequireOwnerSignoffBeforeClose] = useState(false);
    const [requireTemplateCompletionBeforeClose, setRequireTemplateCompletionBeforeClose] = useState(true);
    const [templateLabel, setTemplateLabel] = useState("");
    const [templateGuidance, setTemplateGuidance] = useState("");
    const [templateMessage, setTemplateMessage] = useState("");
    const [templateError, setTemplateError] = useState("");
    const [profileMessage, setProfileMessage] = useState("");
    const [policyMessage, setPolicyMessage] = useState("");
    const [profileError, setProfileError] = useState("");
    const [policyError, setPolicyError] = useState("");
    const [isSavingProfile, setIsSavingProfile] = useState(false);
    const [isSavingPolicy, setIsSavingPolicy] = useState(false);
    const [isSavingTemplate, setIsSavingTemplate] = useState(false);
    const [deletingTemplateId, setDeletingTemplateId] = useState<string | null>(null);

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
    const closeTemplateItemsQuery = useQuery<CloseTemplateItem[], Error>({
        queryKey: ["closeTemplateItems", organizationId],
        enabled: hydrated && Boolean(organizationId),
        queryFn: () => listCloseTemplateItems(organizationId),
    });

    const currentOrganization = (organizationsQuery.data ?? []).find((item) => item.id === organizationId) ?? null;
    const canManageSettings =
        currentOrganization?.role === "OWNER" || currentOrganization?.role === "ADMIN";

    useEffect(() => {
        if (settingsQuery.data) {
            setWorkspaceName(settingsQuery.data.name);
            setWorkspaceTimezone(settingsQuery.data.timezone);
            setInvitationTtlDays(String(settingsQuery.data.invitationTtlDays));
            setCloseMaterialityThreshold(String(settingsQuery.data.closeMaterialityThreshold));
            setMinimumCloseNotesRequired(String(settingsQuery.data.minimumCloseNotesRequired));
            setRequireSignoffBeforeClose(settingsQuery.data.requireSignoffBeforeClose);
            setMinimumSignoffCount(String(settingsQuery.data.minimumSignoffCount));
            setRequireOwnerSignoffBeforeClose(settingsQuery.data.requireOwnerSignoffBeforeClose);
            setRequireTemplateCompletionBeforeClose(settingsQuery.data.requireTemplateCompletionBeforeClose);
        }
    }, [settingsQuery.data]);

    const queryError =
        organizationsQuery.error?.message ??
        settingsQuery.error?.message ??
        closeTemplateItemsQuery.error?.message ??
        "";

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
                closeMaterialityThreshold: Number(closeMaterialityThreshold),
                minimumCloseNotesRequired: Number(minimumCloseNotesRequired),
                requireSignoffBeforeClose,
                minimumSignoffCount: Number(minimumSignoffCount),
                requireOwnerSignoffBeforeClose,
                requireTemplateCompletionBeforeClose,
            });
            await Promise.all([
                queryClient.invalidateQueries({ queryKey: ["workspaceSettings", organizationId] }),
                queryClient.invalidateQueries({ queryKey: ["organizations"] }),
            ]);
            setInvitationTtlDays(String(updated.invitationTtlDays));
            setCloseMaterialityThreshold(String(updated.closeMaterialityThreshold));
            setMinimumCloseNotesRequired(String(updated.minimumCloseNotesRequired));
            setRequireSignoffBeforeClose(updated.requireSignoffBeforeClose);
            setMinimumSignoffCount(String(updated.minimumSignoffCount));
            setRequireOwnerSignoffBeforeClose(updated.requireOwnerSignoffBeforeClose);
            setRequireTemplateCompletionBeforeClose(updated.requireTemplateCompletionBeforeClose);
            setPolicyMessage(
                `Close policy updated: ${updated.invitationTtlDays}-day invites, $${updated.closeMaterialityThreshold} materiality, ${updated.minimumCloseNotesRequired} required close note(s), and ${updated.minimumSignoffCount} required signoff(s).`
            );
        } catch (saveError) {
            setPolicyError(saveError instanceof Error ? saveError.message : "Unable to save workspace settings.");
        } finally {
            setIsSavingPolicy(false);
        }
    }

    async function handleTemplateSubmit(event: FormEvent<HTMLFormElement>) {
        event.preventDefault();
        if (!organizationId) return;

        setTemplateError("");
        setTemplateMessage("");
        setIsSavingTemplate(true);

        try {
            const item = await createCloseTemplateItem(organizationId, {
                label: templateLabel,
                guidance: templateGuidance,
            });
            queryClient.setQueryData<CloseTemplateItem[]>(
                ["closeTemplateItems", organizationId],
                (current) => [...(current ?? []), item]
            );
            await queryClient.invalidateQueries({ queryKey: ["closeTemplateItems", organizationId] });
            setTemplateLabel("");
            setTemplateGuidance("");
            setTemplateMessage(`Close playbook item "${item.label}" added.`);
        } catch (saveError) {
            setTemplateError(saveError instanceof Error ? saveError.message : "Unable to save close playbook item.");
        } finally {
            setIsSavingTemplate(false);
        }
    }

    async function handleDeleteTemplate(itemId: string, label: string) {
        if (!organizationId) return;
        setTemplateError("");
        setTemplateMessage("");
        setDeletingTemplateId(itemId);
        try {
            await deleteCloseTemplateItem(organizationId, itemId);
            queryClient.setQueryData<CloseTemplateItem[]>(
                ["closeTemplateItems", organizationId],
                (current) => (current ?? []).filter((item) => item.id !== itemId)
            );
            await queryClient.invalidateQueries({ queryKey: ["closeTemplateItems", organizationId] });
            setTemplateMessage(`Close playbook item "${label}" removed.`);
        } catch (deleteError) {
            setTemplateError(deleteError instanceof Error ? deleteError.message : "Unable to delete close playbook item.");
        } finally {
            setDeletingTemplateId(null);
        }
    }

    if (!hydrated || organizationsQuery.isLoading || settingsQuery.isLoading || closeTemplateItemsQuery.isLoading) {
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
                        <SummaryMetric
                            label="Close standard"
                            value={`$${settingsQuery.data?.closeMaterialityThreshold ?? currentOrganization?.closeMaterialityThreshold ?? 500}`}
                            detail={`Requires ${settingsQuery.data?.minimumCloseNotesRequired ?? currentOrganization?.minimumCloseNotesRequired ?? 1} close note(s), ${settingsQuery.data?.minimumSignoffCount ?? currentOrganization?.minimumSignoffCount ?? 1} signoff(s)${(settingsQuery.data?.requireOwnerSignoffBeforeClose ?? currentOrganization?.requireOwnerSignoffBeforeClose ?? false) ? ", including an owner" : ""}${(settingsQuery.data?.requireTemplateCompletionBeforeClose ?? currentOrganization?.requireTemplateCompletionBeforeClose ?? true) ? ", and completed recurring playbook items" : ""}.`}
                            tone="default"
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
                    label="Close materiality"
                    value={`$${settingsQuery.data?.closeMaterialityThreshold ?? 500}`}
                    detail={`Months should usually carry at least ${settingsQuery.data?.minimumCloseNotesRequired ?? 1} note(s) and ${settingsQuery.data?.minimumSignoffCount ?? 1} signoff(s)${(settingsQuery.data?.requireOwnerSignoffBeforeClose ?? false) ? ", including an owner," : ""} before close.`}
                />
                <SummaryMetric
                    label="Policy owner"
                    value={canManageSettings ? "Operator-managed" : "Read only"}
                    detail="Members can see workspace profile, but not change invitation or close policy."
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
                title="Control invite lifetime and close standards"
                description="Set how long invitation links stay valid and what month-end evidence should exist before the team treats a close as truly ready."
            >
                {canManageSettings ? (
                    <form className="grid gap-4 lg:grid-cols-2" onSubmit={handlePolicySubmit}>
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
                        <label className="space-y-2">
                            <span className="text-sm text-zinc-300">Materiality threshold ($)</span>
                            <input
                                type="number"
                                min={0}
                                max={1000000}
                                step={0.01}
                                value={closeMaterialityThreshold}
                                onChange={(event) => setCloseMaterialityThreshold(event.target.value)}
                                className="w-full rounded-md border border-zinc-800 bg-black px-3 py-2 text-sm text-white outline-none transition hover:border-zinc-700"
                                required
                            />
                            <p className="text-xs text-zinc-500">
                                Use this as the line where unresolved dollar exposure should feel material enough to keep the month visibly cautious.
                            </p>
                        </label>
                        <label className="space-y-2">
                            <span className="text-sm text-zinc-300">Minimum close notes required</span>
                            <input
                                type="number"
                                min={0}
                                max={10}
                                step={1}
                                value={minimumCloseNotesRequired}
                                onChange={(event) => setMinimumCloseNotesRequired(event.target.value)}
                                className="w-full rounded-md border border-zinc-800 bg-black px-3 py-2 text-sm text-white outline-none transition hover:border-zinc-700"
                                required
                            />
                            <p className="text-xs text-zinc-500">
                                Helpful for making sure each month leaves enough operator context behind for the next reviewer.
                            </p>
                        </label>
                        <label className="space-y-2">
                            <span className="text-sm text-zinc-300">Minimum signoffs required</span>
                            <input
                                type="number"
                                min={0}
                                max={10}
                                step={1}
                                value={minimumSignoffCount}
                                onChange={(event) => setMinimumSignoffCount(event.target.value)}
                                className="w-full rounded-md border border-zinc-800 bg-black px-3 py-2 text-sm text-white outline-none transition hover:border-zinc-700"
                                required
                            />
                            <p className="text-xs text-zinc-500">
                                Use this when close should reflect more than one approving set of eyes before it is treated as complete.
                            </p>
                        </label>
                        <label className="space-y-3 rounded-lg border border-white/10 bg-white/[0.03] p-4">
                            <span className="text-sm font-medium text-zinc-200">Require sign-off before close</span>
                            <p className="text-xs leading-5 text-zinc-500">
                                Turn this on when every month should carry explicit owner or admin approval before it is treated as ready to close.
                            </p>
                            <div className="flex items-center gap-3">
                                <input
                                    id="require-signoff-before-close"
                                    type="checkbox"
                                    checked={requireSignoffBeforeClose}
                                    onChange={(event) => setRequireSignoffBeforeClose(event.target.checked)}
                                    className="h-4 w-4 rounded border-zinc-700 bg-black text-emerald-300 focus:ring-emerald-300"
                                />
                                <label htmlFor="require-signoff-before-close" className="text-sm text-zinc-300">
                                    Sign-off is part of the close standard
                                </label>
                            </div>
                        </label>
                        <label className="space-y-3 rounded-lg border border-white/10 bg-white/[0.03] p-4">
                            <span className="text-sm font-medium text-zinc-200">Require owner sign-off</span>
                            <p className="text-xs leading-5 text-zinc-500">
                                Turn this on when at least one owner must explicitly approve the month before a standard close can go through.
                            </p>
                            <div className="flex items-center gap-3">
                                <input
                                    id="require-owner-signoff-before-close"
                                    type="checkbox"
                                    checked={requireOwnerSignoffBeforeClose}
                                    onChange={(event) => setRequireOwnerSignoffBeforeClose(event.target.checked)}
                                    className="h-4 w-4 rounded border-zinc-700 bg-black text-emerald-300 focus:ring-emerald-300"
                                />
                                <label htmlFor="require-owner-signoff-before-close" className="text-sm text-zinc-300">
                                    An owner sign-off is required for standard close
                                </label>
                            </div>
                        </label>
                        <label className="space-y-3 rounded-lg border border-white/10 bg-white/[0.03] p-4">
                            <span className="text-sm font-medium text-zinc-200">Require recurring playbook completion</span>
                            <p className="text-xs leading-5 text-zinc-500">
                                Turn this on when month-end should stay open until the recurring close playbook items for that month are actually completed.
                            </p>
                            <div className="flex items-center gap-3">
                                <input
                                    id="require-template-completion-before-close"
                                    type="checkbox"
                                    checked={requireTemplateCompletionBeforeClose}
                                    onChange={(event) => setRequireTemplateCompletionBeforeClose(event.target.checked)}
                                    className="h-4 w-4 rounded border-zinc-700 bg-black text-emerald-300 focus:ring-emerald-300"
                                />
                                <label htmlFor="require-template-completion-before-close" className="text-sm text-zinc-300">
                                    Recurring close playbook items must be completed before close
                                </label>
                            </div>
                        </label>
                        <div className="flex items-end justify-end lg:col-span-2">
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
                        title="Ask an owner or admin to change close policy"
                        message="Members can use the workspace normally, but invite and month-end policy settings are restricted to operators."
                    />
                )}
            </SectionBand>

            <SectionBand
                eyebrow="Close playbook"
                title="Persist recurring month-end checks"
                description="Capture the standing reminders your team wants to see every month, so the runbook reflects your actual accounting practice instead of memory or side notes."
            >
                {templateError ? <StatusBanner tone="error" title="Close playbook update failed" message={templateError} /> : null}
                {templateMessage ? <StatusBanner tone="success" title="Close playbook updated" message={templateMessage} /> : null}

                {canManageSettings ? (
                    <div className="space-y-5">
                        <form className="grid gap-4 lg:grid-cols-2" onSubmit={handleTemplateSubmit}>
                            <label className="space-y-2">
                                <span className="text-sm text-zinc-300">Template item label</span>
                                <input
                                    type="text"
                                    maxLength={120}
                                    value={templateLabel}
                                    onChange={(event) => setTemplateLabel(event.target.value)}
                                    placeholder="Confirm payroll liabilities were tied out"
                                    className="w-full rounded-md border border-zinc-800 bg-black px-3 py-2 text-sm text-white outline-none transition hover:border-zinc-700"
                                    required
                                />
                            </label>
                            <label className="space-y-2">
                                <span className="text-sm text-zinc-300">Operator guidance</span>
                                <textarea
                                    value={templateGuidance}
                                    onChange={(event) => setTemplateGuidance(event.target.value)}
                                    placeholder="Use payroll provider reports and the liability rollforward before treating this as complete."
                                    rows={3}
                                    maxLength={500}
                                    className="w-full rounded-md border border-zinc-800 bg-black px-3 py-2 text-sm text-white outline-none transition hover:border-zinc-700"
                                    required
                                />
                            </label>
                            <div className="flex justify-end lg:col-span-2">
                                <button
                                    type="submit"
                                    disabled={isSavingTemplate}
                                    className="rounded-md bg-emerald-300 px-4 py-2.5 text-sm font-semibold text-black hover:bg-emerald-200 disabled:opacity-50"
                                >
                                    {isSavingTemplate ? "Saving..." : "Add playbook item"}
                                </button>
                            </div>
                        </form>

                        {(closeTemplateItemsQuery.data ?? []).length === 0 ? (
                            <StatusBanner
                                tone="muted"
                                title="No recurring close playbook items yet"
                                message="Add the standing checks your team expects every month so the runbook stays grounded in your real close habits."
                            />
                        ) : (
                            <div className="space-y-3">
                                {(closeTemplateItemsQuery.data ?? []).map((item) => (
                                    <div key={item.id} className="rounded-lg border border-white/10 bg-white/[0.03] p-4">
                                        <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
                                            <div>
                                                <p className="text-sm font-semibold text-white">{item.sortOrder}. {item.label}</p>
                                                <p className="mt-2 text-sm leading-6 text-zinc-400">{item.guidance}</p>
                                            </div>
                                            <button
                                                type="button"
                                                onClick={() => void handleDeleteTemplate(item.id, item.label)}
                                                disabled={deletingTemplateId === item.id}
                                                className="rounded-md border border-white/10 px-3 py-2 text-sm text-zinc-200 hover:bg-white/[0.05] disabled:opacity-50"
                                            >
                                                {deletingTemplateId === item.id ? "Removing..." : "Remove"}
                                            </button>
                                        </div>
                                    </div>
                                ))}
                            </div>
                        )}
                    </div>
                ) : (
                    <StatusBanner
                        tone="muted"
                        title="Ask an owner or admin to change the close playbook"
                        message="Members can follow the month-end runbook, but recurring close standards are managed by operators."
                    />
                )}
            </SectionBand>
        </main>
    );
}
