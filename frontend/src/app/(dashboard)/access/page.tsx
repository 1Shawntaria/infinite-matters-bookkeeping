"use client";

import { FormEvent, useMemo, useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import Link from "next/link";
import { LoadingPanel, PageHero, SectionBand, StatusBanner, SummaryMetric } from "@/components/app-surfaces";
import { addMembershipByEmail, listMemberships, MembershipDetail, updateMembershipRole } from "@/lib/api/access";
import { listOrganizations, OrganizationSummary } from "@/lib/api/auth";
import { useOrganizationSession } from "@/lib/auth/session";

const MANAGEABLE_ROLES = ["ADMIN", "MEMBER"] as const;

function formatRole(value: string | null | undefined) {
    if (!value) return "Unknown";
    return value.toLowerCase().replace(/\b\w/g, (match) => match.toUpperCase());
}

function formatTimestamp(value: string) {
    return new Date(value).toLocaleDateString("en-US", {
        month: "short",
        day: "numeric",
        year: "numeric",
    });
}

export default function AccessPage() {
    const { organizationId, hydrated } = useOrganizationSession();
    const queryClient = useQueryClient();
    const [inviteEmail, setInviteEmail] = useState("");
    const [inviteRole, setInviteRole] = useState<(typeof MANAGEABLE_ROLES)[number]>("MEMBER");
    const [workingMembershipId, setWorkingMembershipId] = useState<string | null>(null);
    const [bannerMessage, setBannerMessage] = useState("");
    const [bannerError, setBannerError] = useState("");

    const organizationsQuery = useQuery<OrganizationSummary[], Error>({
        queryKey: ["organizations"],
        enabled: hydrated,
        queryFn: () => listOrganizations(),
    });
    const currentOrganization = (organizationsQuery.data ?? []).find((item) => item.id === organizationId) ?? null;
    const canManageAccess =
        currentOrganization?.role === "OWNER" || currentOrganization?.role === "ADMIN";

    const membershipsQuery = useQuery<MembershipDetail[], Error>({
        queryKey: ["memberships", organizationId],
        enabled: hydrated && Boolean(organizationId) && canManageAccess,
        queryFn: () => listMemberships(organizationId),
    });

    const loading =
        hydrated && organizationId
            ? organizationsQuery.isLoading || (canManageAccess && membershipsQuery.isLoading)
            : false;
    const queryError =
        organizationsQuery.error?.message ??
        membershipsQuery.error?.message ??
        "";
    const memberships = membershipsQuery.data ?? [];
    const ownerCount = memberships.filter((membership) => membership.role === "OWNER").length;
    const adminCount = memberships.filter((membership) => membership.role === "ADMIN").length;
    const memberCount = memberships.filter((membership) => membership.role === "MEMBER").length;

    const sortedMemberships = useMemo(() => {
        return [...(membershipsQuery.data ?? [])].sort((left, right) => {
            const roleRank = { OWNER: 0, ADMIN: 1, MEMBER: 2 };
            const rankDifference =
                (roleRank[left.role as keyof typeof roleRank] ?? 3) -
                (roleRank[right.role as keyof typeof roleRank] ?? 3);
            if (rankDifference !== 0) return rankDifference;
            return left.user.fullName.localeCompare(right.user.fullName);
        });
    }, [membershipsQuery.data]);

    async function refreshMemberships() {
        await queryClient.invalidateQueries({ queryKey: ["memberships", organizationId] });
    }

    async function handleInvite(event: FormEvent<HTMLFormElement>) {
        event.preventDefault();
        if (!organizationId) return;

        setBannerError("");
        setBannerMessage("");
        setWorkingMembershipId("invite");

        try {
            await addMembershipByEmail(organizationId, inviteEmail, inviteRole);
            await refreshMemberships();
            setInviteEmail("");
            setInviteRole("MEMBER");
            setBannerMessage("Workspace access updated successfully.");
        } catch (error) {
            setBannerError(error instanceof Error ? error.message : "Unable to add that member.");
        } finally {
            setWorkingMembershipId(null);
        }
    }

    async function handleRoleChange(membershipId: string, role: string) {
        if (!organizationId) return;

        setBannerError("");
        setBannerMessage("");
        setWorkingMembershipId(membershipId);

        try {
            await updateMembershipRole(organizationId, membershipId, role);
            await refreshMemberships();
            setBannerMessage("Member role updated successfully.");
        } catch (error) {
            setBannerError(error instanceof Error ? error.message : "Unable to update that role.");
        } finally {
            setWorkingMembershipId(null);
        }
    }

    if (!hydrated || loading) {
        return (
            <LoadingPanel
                title="Loading access controls."
                message="Checking workspace role, membership roster, and who can administer access."
            />
        );
    }

    if (!organizationId || queryError) {
        return (
            <main className="p-4 sm:p-6 lg:p-8">
                <StatusBanner
                    tone="error"
                    title="Access workspace unavailable"
                    message={queryError || "No organization ID found. Please sign in again."}
                />
            </main>
        );
    }

    return (
        <main className="space-y-8 p-4 sm:p-6 lg:p-8">
            <PageHero
                eyebrow="Workspace access"
                title="Access"
                description="Keep a clear view of who can operate in this workspace, what level they hold, and who is allowed to manage access."
                aside={
                    <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-1">
                        <SummaryMetric
                            label="Your role"
                            value={formatRole(currentOrganization?.role)}
                            detail="Role is sourced directly from your current organization membership."
                            tone={canManageAccess ? "success" : "default"}
                        />
                        <SummaryMetric
                            label="Workspace"
                            value={currentOrganization?.name ?? "Current workspace"}
                            detail={currentOrganization ? `${currentOrganization.planTier} · ${currentOrganization.timezone}` : "Loading workspace context."}
                        />
                    </div>
                }
            >
                <div className="flex flex-wrap gap-3">
                    <Link
                        href="/notifications"
                        className="rounded-md border border-white/10 px-4 py-2.5 text-sm text-zinc-100 hover:bg-white/[0.05]"
                    >
                        Open notifications
                    </Link>
                    <Link
                        href="/dashboard"
                        className="rounded-md bg-emerald-300 px-4 py-2.5 text-sm font-semibold text-black hover:bg-emerald-200"
                    >
                        Back to dashboard
                    </Link>
                </div>
            </PageHero>

            {bannerError ? (
                <StatusBanner
                    tone="error"
                    title="Access change failed"
                    message={bannerError}
                />
            ) : null}

            {bannerMessage ? (
                <StatusBanner
                    tone="success"
                    title="Access updated"
                    message={bannerMessage}
                />
            ) : null}

            {canManageAccess ? (
                <>
                    <div className="grid gap-4 md:grid-cols-3">
                        <SummaryMetric
                            label="Owners"
                            value={`${ownerCount}`}
                            detail="Owner memberships are visible but not editable from this lightweight access surface."
                        />
                        <SummaryMetric
                            label="Admins"
                            value={`${adminCount}`}
                            detail="Admins can run operational workflows and manage most workspace settings."
                        />
                        <SummaryMetric
                            label="Members"
                            value={`${memberCount}`}
                            detail="Members can work inside the product without access to operator controls."
                        />
                    </div>

                    <SectionBand
                        eyebrow="Invite existing user"
                        title="Grant workspace access"
                        description="Attach an existing app user to this workspace by email and choose whether they should be an admin or a member."
                    >
                        <form className="grid gap-4 lg:grid-cols-[1.3fr_0.7fr_auto]" onSubmit={handleInvite}>
                            <label className="space-y-2">
                                <span className="text-sm text-zinc-300">User email</span>
                                <input
                                    type="email"
                                    value={inviteEmail}
                                    onChange={(event) => setInviteEmail(event.target.value)}
                                    placeholder="teammate@company.com"
                                    className="w-full rounded-md border border-zinc-800 bg-black px-3 py-2 text-sm text-white outline-none transition hover:border-zinc-700"
                                    required
                                />
                            </label>
                            <label className="space-y-2">
                                <span className="text-sm text-zinc-300">Role</span>
                                <select
                                    value={inviteRole}
                                    onChange={(event) => setInviteRole(event.target.value as (typeof MANAGEABLE_ROLES)[number])}
                                    className="w-full rounded-md border border-zinc-800 bg-black px-3 py-2 text-sm text-white outline-none transition hover:border-zinc-700"
                                >
                                    {MANAGEABLE_ROLES.map((role) => (
                                        <option key={role} value={role}>
                                            {formatRole(role)}
                                        </option>
                                    ))}
                                </select>
                            </label>
                            <div className="flex items-end">
                                <button
                                    type="submit"
                                    disabled={workingMembershipId === "invite"}
                                    className="rounded-md bg-emerald-300 px-4 py-2.5 text-sm font-semibold text-black hover:bg-emerald-200 disabled:opacity-50"
                                >
                                    {workingMembershipId === "invite" ? "Updating..." : "Grant access"}
                                </button>
                            </div>
                        </form>
                    </SectionBand>

                    <SectionBand
                        eyebrow="Membership roster"
                        title="People with access"
                        description="Review current access and adjust non-owner roles when workspace responsibilities change."
                    >
                        <div className="space-y-3">
                            {sortedMemberships.map((membership) => {
                                const canEdit = membership.role !== "OWNER";
                                const busy = workingMembershipId === membership.id;

                                return (
                                    <div
                                        key={membership.id}
                                        className="rounded-lg border border-white/10 bg-white/[0.03] px-4 py-4"
                                    >
                                        <div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
                                            <div className="space-y-1">
                                                <div className="flex flex-wrap items-center gap-2">
                                                    <p className="text-sm font-semibold text-white">
                                                        {membership.user.fullName}
                                                    </p>
                                                    <span className="rounded-full border border-white/10 bg-white/[0.04] px-2 py-1 text-[11px] uppercase tracking-[0.14em] text-zinc-300">
                                                        {membership.role}
                                                    </span>
                                                </div>
                                                <p className="text-sm text-zinc-300">{membership.user.email}</p>
                                                <p className="text-xs text-zinc-500">
                                                    Added {formatTimestamp(membership.createdAt)}
                                                </p>
                                            </div>

                                            {canEdit ? (
                                                <div className="flex items-center gap-2">
                                                    <select
                                                        value={membership.role}
                                                        onChange={(event) =>
                                                            handleRoleChange(membership.id, event.target.value)
                                                        }
                                                        disabled={busy}
                                                        className="rounded-md border border-zinc-800 bg-black px-3 py-2 text-sm text-white outline-none transition hover:border-zinc-700 disabled:opacity-50"
                                                    >
                                                        {MANAGEABLE_ROLES.map((role) => (
                                                            <option key={role} value={role}>
                                                                {formatRole(role)}
                                                            </option>
                                                        ))}
                                                    </select>
                                                    {busy ? (
                                                        <span className="text-xs text-zinc-500">Saving...</span>
                                                    ) : null}
                                                </div>
                                            ) : (
                                                <p className="text-xs text-zinc-500">
                                                    Owner role is fixed here for safety.
                                                </p>
                                            )}
                                        </div>
                                    </div>
                                );
                            })}
                        </div>
                    </SectionBand>
                </>
            ) : (
                <SectionBand
                    eyebrow="Read-only access"
                    title="Access changes require an operator role"
                    description="Workspace members can use the product normally, but only owners and admins can open the membership roster and change access."
                >
                    <StatusBanner
                        tone="muted"
                        title="Ask an owner or admin for access changes"
                        message="If you need someone added, removed, or promoted, a workspace operator can handle it from this page."
                    />
                </SectionBand>
            )}
        </main>
    );
}
