"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { useParams, useRouter } from "next/navigation";
import { BrandLogo } from "@/components/brand-logo";
import {
    acceptInvitation,
    getInvitationPreview,
    listOrganizations,
    InvitationPreview,
} from "@/lib/api/auth";
import { storeAuthSession } from "@/lib/auth/session";

function formatRole(value: string) {
    return value.toLowerCase().replace(/\b\w/g, (match) => match.toUpperCase());
}

function formatTimestamp(value: string) {
    return new Date(value).toLocaleDateString("en-US", {
        month: "short",
        day: "numeric",
        year: "numeric",
    });
}

export default function InvitationPage() {
    const router = useRouter();
    const params = useParams<{ token: string }>();
    const [token, setToken] = useState("");
    const [invitation, setInvitation] = useState<InvitationPreview | null>(null);
    const [currentUserEmail, setCurrentUserEmail] = useState<string | null>(null);
    const [fullName, setFullName] = useState("");
    const [password, setPassword] = useState("");
    const [error, setError] = useState("");
    const [isSubmitting, setIsSubmitting] = useState(false);
    const [isLoading, setIsLoading] = useState(true);

    useEffect(() => {
        setToken(params.token);
    }, [params]);

    useEffect(() => {
        if (!token) return;

        let cancelled = false;

        async function load() {
            setIsLoading(true);
            setError("");

            try {
                const preview = await getInvitationPreview(token);
                if (cancelled) return;
                setInvitation(preview);

                const response = await fetch(
                    `${process.env.NEXT_PUBLIC_API_BASE_URL || "http://localhost:8080"}/api/auth/me`,
                    { credentials: "include" }
                );
                if (!cancelled && response.ok) {
                    const currentUser = (await response.json()) as { email: string };
                    setCurrentUserEmail(currentUser.email);
                }
            } catch (loadError) {
                if (!cancelled) {
                    setError(loadError instanceof Error ? loadError.message : "Unable to load invitation.");
                }
            } finally {
                if (!cancelled) {
                    setIsLoading(false);
                }
            }
        }

        load();
        return () => {
            cancelled = true;
        };
    }, [token]);

    async function finalizeInvitation() {
        const organizations = await listOrganizations();
        const organization = organizations.find((item) => item.id === invitation?.organizationId) ?? organizations[0];
        if (!organization) {
            throw new Error("No organization is available after accepting this invitation.");
        }
        storeAuthSession(organization.id);
        router.push("/dashboard");
    }

    async function handleCreateAccountAndAccept(event: React.FormEvent<HTMLFormElement>) {
        event.preventDefault();
        if (!token) return;
        setError("");
        setIsSubmitting(true);

        try {
            await acceptInvitation(token, { fullName, password });
            await finalizeInvitation();
        } catch (submitError) {
            setError(submitError instanceof Error ? submitError.message : "Unable to accept invitation.");
        } finally {
            setIsSubmitting(false);
        }
    }

    async function handleAcceptWithCurrentAccount() {
        if (!token) return;
        setError("");
        setIsSubmitting(true);

        try {
            await acceptInvitation(token);
            await finalizeInvitation();
        } catch (submitError) {
            setError(submitError instanceof Error ? submitError.message : "Unable to accept invitation.");
        } finally {
            setIsSubmitting(false);
        }
    }

    if (isLoading) {
        return (
            <main className="mx-auto flex min-h-screen max-w-3xl items-center justify-center p-6">
                <div className="rounded-lg border border-zinc-800 bg-zinc-950/80 p-6 text-sm text-zinc-300">
                    Loading invitation details.
                </div>
            </main>
        );
    }

    if (error && !invitation) {
        return (
            <main className="mx-auto flex min-h-screen max-w-3xl items-center justify-center p-6">
                <div className="w-full rounded-xl border border-rose-500/30 bg-rose-500/10 p-6 text-rose-100">
                    {error}
                </div>
            </main>
        );
    }

    const canAcceptWithCurrentAccount =
        invitation &&
        currentUserEmail &&
        currentUserEmail.toLowerCase() === invitation.email.toLowerCase() &&
        invitation.status === "PENDING";

    return (
        <main className="mx-auto grid min-h-screen w-full max-w-5xl items-center gap-6 p-6 lg:grid-cols-[1.05fr_0.95fr]">
            <section className="space-y-6 rounded-xl border border-zinc-900/80 bg-black/60 p-8">
                <div className="space-y-3">
                    <BrandLogo
                        variant="alternate"
                        className="h-12 w-auto"
                        alt="Infinite Matters"
                        priority
                    />
                    <h1 className="text-4xl font-semibold text-white">You&apos;ve been invited into a workspace.</h1>
                    <p className="text-base text-zinc-400">
                        Accept this invitation to join the bookkeeping workspace and pick up exactly where the team wants you involved.
                    </p>
                </div>

                {invitation ? (
                    <div className="grid gap-4 sm:grid-cols-2">
                        <div className="rounded-lg border border-zinc-800 bg-zinc-950/70 p-4">
                            <p className="text-sm text-zinc-400">Workspace</p>
                            <p className="mt-1 text-lg font-semibold text-white">{invitation.organizationName}</p>
                        </div>
                        <div className="rounded-lg border border-zinc-800 bg-zinc-950/70 p-4">
                            <p className="text-sm text-zinc-400">Invited role</p>
                            <p className="mt-1 text-lg font-semibold text-white">{formatRole(invitation.role)}</p>
                        </div>
                        <div className="rounded-lg border border-zinc-800 bg-zinc-950/70 p-4">
                            <p className="text-sm text-zinc-400">Invited email</p>
                            <p className="mt-1 text-sm font-medium text-white">{invitation.email}</p>
                        </div>
                        <div className="rounded-lg border border-zinc-800 bg-zinc-950/70 p-4">
                            <p className="text-sm text-zinc-400">Expires</p>
                            <p className="mt-1 text-sm font-medium text-white">{formatTimestamp(invitation.expiresAt)}</p>
                        </div>
                    </div>
                ) : null}
            </section>

            <section className="rounded-xl border border-zinc-900/80 bg-black/60 p-8">
                {error ? (
                    <div className="mb-5 rounded-md border border-rose-500/40 bg-rose-500/10 p-3 text-sm text-rose-200">
                        {error}
                    </div>
                ) : null}

                {invitation?.status !== "PENDING" ? (
                    <div className="space-y-3">
                        <h2 className="text-2xl font-semibold text-white">This invitation is no longer active.</h2>
                        <p className="text-sm text-zinc-400">
                            The invitation may have expired, been revoked, or already been accepted.
                        </p>
                    </div>
                ) : canAcceptWithCurrentAccount ? (
                    <div className="space-y-5">
                        <div className="space-y-2">
                            <h2 className="text-2xl font-semibold text-white">Accept with your current account</h2>
                            <p className="text-sm text-zinc-400">
                                You&apos;re already signed in as {currentUserEmail}. Accepting will add this workspace to your account immediately.
                            </p>
                        </div>
                        <button
                            type="button"
                            disabled={isSubmitting}
                            onClick={handleAcceptWithCurrentAccount}
                            className="rounded-md bg-emerald-400 px-4 py-3 font-medium text-black transition hover:bg-emerald-300 disabled:opacity-50"
                        >
                            {isSubmitting ? "Accepting..." : "Accept invitation"}
                        </button>
                    </div>
                ) : currentUserEmail ? (
                    <div className="space-y-5">
                        <div className="space-y-2">
                            <h2 className="text-2xl font-semibold text-white">This invitation belongs to another email</h2>
                            <p className="text-sm text-zinc-400">
                                You&apos;re currently signed in as {currentUserEmail}, but this invitation is for {invitation?.email}.
                            </p>
                        </div>
                        <Link
                            href={`/login?next=/invite/${token}`}
                            className="inline-flex rounded-md bg-emerald-400 px-4 py-3 font-medium text-black transition hover:bg-emerald-300"
                        >
                            Sign in as the invited user
                        </Link>
                    </div>
                ) : (
                    <div className="space-y-5">
                        <div className="space-y-2">
                            <h2 className="text-2xl font-semibold text-white">Create your account</h2>
                            <p className="text-sm text-zinc-400">
                                Use the invited email to create your account and join the workspace in one step.
                            </p>
                        </div>

                        <form onSubmit={handleCreateAccountAndAccept} className="space-y-4">
                            <div className="space-y-2">
                                <label className="text-sm font-medium text-zinc-300" htmlFor="fullName">
                                    Full name
                                </label>
                                <input
                                    id="fullName"
                                    className="w-full rounded-md border border-zinc-800 bg-zinc-950 px-3 py-3 text-white outline-none transition placeholder:text-zinc-600 focus:border-emerald-400"
                                    value={fullName}
                                    onChange={(event) => setFullName(event.target.value)}
                                    placeholder="Jordan Lee"
                                    required
                                />
                            </div>
                            <div className="space-y-2">
                                <label className="text-sm font-medium text-zinc-300" htmlFor="password">
                                    Password
                                </label>
                                <input
                                    id="password"
                                    type="password"
                                    className="w-full rounded-md border border-zinc-800 bg-zinc-950 px-3 py-3 text-white outline-none transition placeholder:text-zinc-600 focus:border-emerald-400"
                                    value={password}
                                    onChange={(event) => setPassword(event.target.value)}
                                    placeholder="Create a password"
                                    minLength={8}
                                    required
                                />
                            </div>
                            <button
                                type="submit"
                                disabled={isSubmitting}
                                className="w-full rounded-md bg-emerald-400 px-4 py-3 font-medium text-black transition hover:bg-emerald-300 disabled:opacity-50"
                            >
                                {isSubmitting ? "Creating account..." : "Create account and accept"}
                            </button>
                        </form>

                        <p className="text-sm text-zinc-500">
                            Already have an account for {invitation?.email}?{" "}
                            <Link href={`/login?next=/invite/${token}`} className="text-sky-300 hover:text-sky-200">
                                Sign in and come back here
                            </Link>
                            .
                        </p>
                    </div>
                )}
            </section>
        </main>
    );
}
