"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { BrandLogo } from "@/components/brand-logo";
import { listOrganizations, login } from "@/lib/api/auth";
import { storeAuthSession } from "@/lib/auth/session";

export default function LoginPage() {
    const router = useRouter();

    const [email, setEmail] = useState("");
    const [password, setPassword] = useState("");
    const [error, setError] = useState("");
    const [isSubmitting, setIsSubmitting] = useState(false);

    async function handleSubmit(event: React.FormEvent<HTMLFormElement>) {
        event.preventDefault();
        setError("");
        setIsSubmitting(true);

        try {
            await login({ email, password });

            const organizations = await listOrganizations();
            const organization = organizations[0];

            if (!organization) {
                throw new Error("No organization is available for this user.");
            }

            storeAuthSession(organization.id);
            const nextPath =
                typeof window !== "undefined"
                    ? new URLSearchParams(window.location.search).get("next")
                    : null;
            router.push(nextPath && nextPath.startsWith("/") ? nextPath : "/dashboard");
        } catch (err) {
            const message =
                err instanceof Error ? err.message : "Unable to sign in.";
            setError(message);
        } finally {
            setIsSubmitting(false);
        }
    }

    return (
        <main className="grid w-full max-w-6xl overflow-hidden rounded-xl border border-white/10 bg-black/55 shadow-[0_28px_90px_rgba(0,0,0,0.5)] backdrop-blur md:grid-cols-[1.08fr_0.92fr]">
            <section className="flex flex-col justify-between border-b border-white/10 bg-[linear-gradient(180deg,rgba(8,20,31,0.9)_0%,rgba(7,16,24,0.94)_100%)] p-8 md:border-b-0 md:border-r md:p-10">
                <div className="space-y-8">
                    <div className="space-y-5">
                        <BrandLogo
                            variant="brandmarkWordmarkReverse"
                            className="h-11 w-auto"
                            alt="Infinite Matters"
                            priority
                        />
                        <h1 className="max-w-lg text-4xl font-semibold leading-tight text-white">
                            Keep the books moving, even when the work gets messy.
                        </h1>
                        <p className="max-w-lg text-base text-zinc-400">
                            Triage review work, close periods with confidence, and keep every
                            organization aligned on what needs attention next.
                        </p>
                    </div>

                    <div className="grid gap-3 sm:grid-cols-2">
                        <div className="rounded-lg border border-white/10 bg-white/[0.04] p-4">
                            <p className="text-sm font-medium text-white">Close readiness</p>
                            <p className="mt-1 text-sm text-zinc-400">
                                Reconciliation and task status stay visible in one operating view.
                            </p>
                        </div>
                        <div className="rounded-lg border border-white/10 bg-white/[0.04] p-4">
                            <p className="text-sm font-medium text-white">Safer handoffs</p>
                            <p className="mt-1 text-sm text-zinc-400">
                                Workspace context follows the user instead of relying on manual IDs.
                            </p>
                        </div>
                    </div>
                </div>

                <div className="mt-8 overflow-hidden rounded-lg border border-white/10 bg-black/30 p-5">
                    <div className="rounded-lg border border-white/10 bg-white/[0.04] p-5">
                        <div className="flex items-center justify-between gap-4 border-b border-white/10 pb-4">
                            <BrandLogo
                                variant="brandmarkWordmark"
                                className="h-8 w-auto"
                                alt="Infinite Matters"
                            />
                            <span className="rounded-full border border-emerald-400/20 bg-emerald-400/10 px-3 py-1 text-xs font-medium text-emerald-200">
                                Illustrative preview
                            </span>
                        </div>

                        <div className="mt-5 grid gap-3 sm:grid-cols-3">
                            <div className="rounded-lg border border-white/10 bg-black/20 p-4">
                                <p className="text-xs uppercase tracking-[0.18em] text-zinc-400">Workflow</p>
                                <p className="mt-2 text-2xl font-semibold text-emerald-300">Review</p>
                            </div>
                            <div className="rounded-lg border border-white/10 bg-black/20 p-4">
                                <p className="text-xs uppercase tracking-[0.18em] text-zinc-400">Month-end</p>
                                <p className="mt-2 text-2xl font-semibold text-white">Reconcile</p>
                            </div>
                            <div className="rounded-lg border border-white/10 bg-black/20 p-4">
                                <p className="text-xs uppercase tracking-[0.18em] text-zinc-400">Access</p>
                                <p className="mt-2 text-2xl font-semibold text-emerald-300">Secure</p>
                            </div>
                        </div>

                        <div className="mt-4 grid gap-3 sm:grid-cols-[1.2fr_0.8fr]">
                            <div className="rounded-lg border border-white/10 bg-black/20 p-4">
                                <div className="flex items-end justify-between gap-3">
                                    <div>
                                        <p className="text-sm font-medium text-white">Workspace rhythm</p>
                                        <p className="mt-1 text-sm text-zinc-400">A visual cue for review flow, reconciliation, and operational follow-through.</p>
                                    </div>
                                    <p className="text-xs text-zinc-400">Preview</p>
                                </div>
                                <svg viewBox="0 0 240 90" className="mt-4 h-24 w-full" aria-hidden="true" focusable="false">
                                    <path
                                        d="M8 72 C32 68 42 40 62 46 C82 52 90 28 112 32 C132 36 142 18 160 22 C178 26 188 12 206 16 C220 19 228 10 232 8"
                                        fill="none"
                                        stroke="#2FB7A3"
                                        strokeWidth="4"
                                        strokeLinecap="round"
                                    />
                                </svg>
                            </div>
                            <div className="rounded-lg border border-white/10 bg-black/20 p-4">
                                <p className="text-sm font-medium text-white">Inside the app</p>
                                <ul className="mt-3 space-y-3 text-sm text-zinc-400">
                                    <li className="flex items-center justify-between gap-3">
                                        <span>Review queue</span>
                                        <span className="text-white">Guided</span>
                                    </li>
                                    <li className="flex items-center justify-between gap-3">
                                        <span>Reconciliation</span>
                                        <span className="text-white">Focused</span>
                                    </li>
                                    <li className="flex items-center justify-between gap-3">
                                        <span>Workspace access</span>
                                        <span className="text-white">Role-aware</span>
                                    </li>
                                </ul>
                            </div>
                        </div>
                    </div>
                </div>
            </section>

            <section className="bg-black/45 p-8 md:p-10">
                <form
                    onSubmit={handleSubmit}
                    className="mx-auto flex h-full max-w-md flex-col justify-center space-y-5"
                >
                    <div className="space-y-2">
                        <p className="text-xs uppercase tracking-[0.2em] text-zinc-400">Workspace access</p>
                        <h2 className="text-3xl font-semibold text-white">Sign in</h2>
                        <p className="text-sm text-zinc-400">
                            Use your Infinite Matters account to open your workspace.
                        </p>
                    </div>

                    {error ? (
                        <div
                            role="alert"
                            aria-live="assertive"
                            className="rounded-md border border-rose-500/40 bg-rose-500/10 p-3 text-sm text-rose-200"
                        >
                            {error}
                        </div>
                    ) : null}

                    <div className="space-y-2">
                        <label className="text-sm font-medium text-zinc-300" htmlFor="email">
                            Email
                        </label>
                        <input
                            id="email"
                            className="w-full rounded-md border border-zinc-800 bg-zinc-950/90 px-3 py-3 text-white outline-none transition placeholder:text-zinc-600 focus:border-emerald-400"
                            type="email"
                            autoComplete="username"
                            value={email}
                            onChange={(e) => setEmail(e.target.value)}
                            placeholder="owner@acme.test"
                            required
                        />
                    </div>

                    <div className="space-y-2">
                        <label className="text-sm font-medium text-zinc-300" htmlFor="password">
                            Password
                        </label>
                        <input
                            id="password"
                            className="w-full rounded-md border border-zinc-800 bg-zinc-950/90 px-3 py-3 text-white outline-none transition placeholder:text-zinc-600 focus:border-emerald-400"
                            type="password"
                            autoComplete="current-password"
                            value={password}
                            onChange={(e) => setPassword(e.target.value)}
                            placeholder="password123"
                            required
                        />
                    </div>

                    <button
                        type="submit"
                        disabled={isSubmitting}
                        className="rounded-md bg-emerald-400 px-4 py-3 font-medium text-black transition hover:bg-emerald-300 disabled:opacity-50"
                    >
                        {isSubmitting ? "Signing in..." : "Enter workspace"}
                    </button>
                </form>
            </section>
        </main>
    );
}
