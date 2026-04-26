"use client";

import { FormEvent, useMemo, useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import Link from "next/link";
import { LoadingPanel, PageHero, SectionBand, StatusBanner, SummaryMetric } from "@/components/app-surfaces";
import {
    addMembershipByEmail,
    createInvitation,
    listMemberships,
    listInvitations,
    MembershipDetail,
    OrganizationInvitation,
    removeMembership,
    resendInvitation,
    revokeInvitation,
    updateMembershipRole,
} from "@/lib/api/access";
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

function formatDeliveryState(invitation: OrganizationInvitation) {
    if (!invitation.delivery) {
        return "No delivery event";
    }
    if (invitation.delivery.status === "FAILED") {
        return "Delivery failed";
    }
    if (invitation.delivery.sentAt) {
        return "Email accepted";
    }
    if (invitation.delivery.status === "PENDING") {
        return "Delivery queued";
    }
    return invitation.delivery.deliveryState.replaceAll("_", " ");
}

export default function AccessPage() {
    const { organizationId, hydrated } = useOrganizationSession();
    const queryClient = useQueryClient();
    const [inviteEmail, setInviteEmail] = useState("");
    const [inviteRole, setInviteRole] = useState<(typeof MANAGEABLE_ROLES)[number]>("MEMBER");
    const [invitationEmail, setInvitationEmail] = useState("");
    const [invitationRole, setInvitationRole] = useState<(typeof MANAGEABLE_ROLES)[number]>("MEMBER");
    const [workingMembershipId, setWorkingMembershipId] = useState<string | null>(null);
    const [bannerMessage, setBannerMessage] = useState("");
    const [bannerError, setBannerError] = useState("");
    const [inviteLink, setInviteLink] = useState("");

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
    const invitationsQuery = useQuery<OrganizationInvitation[], Error>({
        queryKey: ["invitations", organizationId],
        enabled: hydrated && Boolean(organizationId) && canManageAccess,
        queryFn: () => listInvitations(organizationId),
    });

    const loading =
        hydrated && organizationId
            ? organizationsQuery.isLoading || (canManageAccess && (membershipsQuery.isLoading || invitationsQuery.isLoading))
            : false;
    const queryError =
        organizationsQuery.error?.message ??
        membershipsQuery.error?.message ??
        invitationsQuery.error?.message ??
        "";
    const memberships = membershipsQuery.data ?? [];
    const invitations = invitationsQuery.data ?? [];
    const ownerCount = memberships.filter((membership) => membership.role === "OWNER").length;
    const adminCount = memberships.filter((membership) => membership.role === "ADMIN").length;
    const memberCount = memberships.filter((membership) => membership.role === "MEMBER").length;
    const pendingInvitationCount = invitations.filter((invitation) => invitation.status === "PENDING").length;

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

    async function refreshInvitations() {
        await queryClient.invalidateQueries({ queryKey: ["invitations", organizationId] });
    }

    async function handleInvite(event: FormEvent<HTMLFormElement>) {
        event.preventDefault();
        if (!organizationId) return;

        setBannerError("");
        setBannerMessage("");
        setInviteLink("");
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

    async function handleInvitation(event: FormEvent<HTMLFormElement>) {
        event.preventDefault();
        if (!organizationId) return;

        setBannerError("");
        setBannerMessage("");
        setInviteLink("");
        setWorkingMembershipId("invitation");

        try {
            const invitation = await createInvitation(organizationId, invitationEmail, invitationRole);
            await refreshInvitations();
            setInvitationEmail("");
            setInvitationRole("MEMBER");
            setBannerMessage("Invitation created successfully and queued for delivery.");
            setInviteLink(invitation.inviteUrl ?? "");
        } catch (error) {
            setBannerError(error instanceof Error ? error.message : "Unable to create that invitation.");
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

    async function handleRemoveMembership(membership: MembershipDetail) {
        if (!organizationId) return;

        setBannerError("");
        setBannerMessage("");
        setWorkingMembershipId(`remove-${membership.id}`);

        try {
            await removeMembership(organizationId, membership.id);
            await refreshMemberships();
            setBannerMessage(`${membership.user.fullName} no longer has workspace access.`);
        } catch (error) {
            setBannerError(error instanceof Error ? error.message : "Unable to remove that member.");
        } finally {
            setWorkingMembershipId(null);
        }
    }

    async function handleRevokeInvitation(invitation: OrganizationInvitation) {
        if (!organizationId) return;

        setBannerError("");
        setBannerMessage("");
        setInviteLink("");
        setWorkingMembershipId(`revoke-${invitation.id}`);

        try {
            await revokeInvitation(organizationId, invitation.id);
            await refreshInvitations();
            setBannerMessage(`Invitation for ${invitation.email} has been revoked.`);
        } catch (error) {
            setBannerError(error instanceof Error ? error.message : "Unable to revoke that invitation.");
        } finally {
            setWorkingMembershipId(null);
        }
    }

    async function handleResendInvitation(invitation: OrganizationInvitation) {
        if (!organizationId) return;

        setBannerError("");
        setBannerMessage("");
        setInviteLink("");
        setWorkingMembershipId(`resend-${invitation.id}`);

        try {
            const resentInvitation = await resendInvitation(organizationId, invitation.id);
            await refreshInvitations();
            setInviteLink(resentInvitation.inviteUrl ?? "");
            setBannerMessage(`Invitation for ${invitation.email} was resent and expiry was renewed.`);
        } catch (error) {
            setBannerError(error instanceof Error ? error.message : "Unable to resend that invitation.");
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
                    message={inviteLink ? `${bannerMessage} Share the invite link below before it expires.` : bannerMessage}
                />
            ) : null}

            {inviteLink ? (
                <SectionBand
                    eyebrow="One-time invite link"
                    title="Share this invitation"
                    description="This link lets the invited person create an account or accept the invite with an existing matching email."
                >
                    <div className="rounded-lg border border-emerald-400/20 bg-emerald-400/5 p-4">
                        <p className="break-all text-sm text-emerald-100">{inviteLink}</p>
                    </div>
                </SectionBand>
            ) : null}

            {canManageAccess ? (
                <>
                    <div className="grid gap-4 md:grid-cols-3">
                        <SummaryMetric
                            label="Owners"
                            value={`${ownerCount}`}
                            detail="Owners stay fixed here unless another owner is already in place. That keeps each workspace recoverable."
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
                        <SummaryMetric
                            label="Pending invites"
                            value={`${pendingInvitationCount}`}
                            detail="Pending invitations expire automatically and can be revoked before acceptance."
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
                        eyebrow="Invite new teammate"
                        title="Pending invitations"
                        description="Create an invitation link for someone who does not have workspace access yet. They can accept it by signing in or creating an account with the invited email."
                    >
                        <form className="grid gap-4 lg:grid-cols-[1.3fr_0.7fr_auto]" onSubmit={handleInvitation}>
                            <label className="space-y-2">
                                <span className="text-sm text-zinc-300">Invite email</span>
                                <input
                                    type="email"
                                    value={invitationEmail}
                                    onChange={(event) => setInvitationEmail(event.target.value)}
                                    placeholder="new.hire@company.com"
                                    className="w-full rounded-md border border-zinc-800 bg-black px-3 py-2 text-sm text-white outline-none transition hover:border-zinc-700"
                                    required
                                />
                            </label>
                            <label className="space-y-2">
                                <span className="text-sm text-zinc-300">Role</span>
                                <select
                                    value={invitationRole}
                                    onChange={(event) => setInvitationRole(event.target.value as (typeof MANAGEABLE_ROLES)[number])}
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
                                    disabled={workingMembershipId === "invitation"}
                                    className="rounded-md bg-sky-300 px-4 py-2.5 text-sm font-semibold text-black hover:bg-sky-200 disabled:opacity-50"
                                >
                                    {workingMembershipId === "invitation" ? "Creating..." : "Create invite"}
                                </button>
                            </div>
                        </form>

                        <div className="mt-6 space-y-3">
                            {invitations.length === 0 ? (
                                <p className="text-sm text-zinc-500">No invitations have been created for this workspace yet.</p>
                            ) : (
                                invitations.map((invitation) => {
                                    const busy =
                                        workingMembershipId === `revoke-${invitation.id}` ||
                                        workingMembershipId === `resend-${invitation.id}`;
                                    const pending = invitation.status === "PENDING";
                                    const canResend =
                                        invitation.status === "PENDING" || invitation.status === "EXPIRED";
                                    return (
                                        <div
                                            key={invitation.id}
                                            className="rounded-lg border border-white/10 bg-white/[0.03] px-4 py-4"
                                        >
                                            <div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
                                                <div className="space-y-1">
                                                    <div className="flex flex-wrap items-center gap-2">
                                                        <p className="text-sm font-semibold text-white">{invitation.email}</p>
                                                        <span className="rounded-full border border-white/10 bg-white/[0.04] px-2 py-1 text-[11px] uppercase tracking-[0.14em] text-zinc-300">
                                                            {invitation.role}
                                                        </span>
                                                        <span className="rounded-full border border-sky-400/20 bg-sky-400/10 px-2 py-1 text-[11px] uppercase tracking-[0.14em] text-sky-100">
                                                            {invitation.status}
                                                        </span>
                                                    </div>
                                                    <p className="text-xs text-zinc-500">
                                                        Expires {formatTimestamp(invitation.expiresAt)}
                                                    </p>
                                                    <p className="text-xs text-zinc-500">
                                                        {formatDeliveryState(invitation)}
                                                        {invitation.delivery?.sentAt
                                                            ? ` · ${formatTimestamp(invitation.delivery.sentAt)}`
                                                            : invitation.delivery?.scheduledFor
                                                              ? ` · queued ${formatTimestamp(invitation.delivery.scheduledFor)}`
                                                              : ""}
                                                    </p>
                                                    {invitation.delivery?.lastError ? (
                                                        <p className="text-xs text-amber-200">
                                                            {invitation.delivery.lastError}
                                                        </p>
                                                    ) : null}
                                                </div>

                                                {pending || canResend ? (
                                                    <div className="flex items-center gap-2">
                                                        {canResend ? (
                                                            <button
                                                                type="button"
                                                                onClick={() => handleResendInvitation(invitation)}
                                                                disabled={busy}
                                                                className="rounded-md border border-sky-400/30 px-3 py-2 text-sm text-sky-100 hover:bg-sky-400/10 disabled:opacity-50"
                                                            >
                                                                Resend invite
                                                            </button>
                                                        ) : null}
                                                        {pending ? (
                                                            <button
                                                                type="button"
                                                                onClick={() => handleRevokeInvitation(invitation)}
                                                                disabled={busy}
                                                                className="rounded-md border border-rose-400/30 px-3 py-2 text-sm text-rose-100 hover:bg-rose-400/10 disabled:opacity-50"
                                                            >
                                                                Revoke invite
                                                            </button>
                                                        ) : null}
                                                        {busy ? <span className="text-xs text-zinc-500">Saving...</span> : null}
                                                    </div>
                                                ) : null}
                                            </div>
                                        </div>
                                    );
                                })
                            )}
                        </div>
                    </SectionBand>

                    <SectionBand
                        eyebrow="Membership roster"
                        title="People with access"
                        description="Review current access and adjust non-owner roles when workspace responsibilities change."
                    >
                        <div className="space-y-3">
                            {sortedMemberships.map((membership) => {
                                const canEdit = membership.role !== "OWNER";
                                const busy =
                                    workingMembershipId === membership.id ||
                                    workingMembershipId === `remove-${membership.id}`;

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
                                                <div className="flex flex-wrap items-center gap-2">
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
                                                    <button
                                                        type="button"
                                                        onClick={() => handleRemoveMembership(membership)}
                                                        disabled={busy}
                                                        className="rounded-md border border-rose-400/30 px-3 py-2 text-sm text-rose-100 hover:bg-rose-400/10 disabled:opacity-50"
                                                    >
                                                        Remove access
                                                    </button>
                                                    {busy ? (
                                                        <span className="text-xs text-zinc-500">Saving...</span>
                                                    ) : null}
                                                </div>
                                            ) : (
                                                <p className="text-xs text-zinc-500">
                                                    Owner access stays protected here so the workspace always keeps a recoverable operator path.
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
