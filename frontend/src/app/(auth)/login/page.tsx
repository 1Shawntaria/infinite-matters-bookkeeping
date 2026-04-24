"use client";

import Image from "next/image";
import { useState } from "react";
import { useRouter } from "next/navigation";
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

            router.push("/dashboard");
        } catch (err) {
            const message =
                err instanceof Error ? err.message : "Unable to sign in.";
            setError(message);
        } finally {
            setIsSubmitting(false);
        }
    }

    return (
        <main className="grid w-full max-w-6xl overflow-hidden rounded-xl border border-zinc-900/80 bg-black/60 shadow-[0_24px_80px_rgba(0,0,0,0.45)] backdrop-blur md:grid-cols-[1.1fr_0.9fr]">
            <section className="flex flex-col justify-between border-b border-zinc-900/80 p-8 md:border-b-0 md:border-r md:p-10">
                <div className="space-y-6">
                    <div className="space-y-3">
                        <p className="text-xs uppercase tracking-[0.24em] text-emerald-300">
                            Infinite Matters
                        </p>
                        <h1 className="max-w-lg text-4xl font-semibold leading-tight text-white">
                            Keep the books moving, even when the work gets messy.
                        </h1>
                        <p className="max-w-lg text-base text-zinc-400">
                            Triage review work, close periods with confidence, and keep every
                            organization aligned on what needs attention next.
                        </p>
                    </div>

                    <div className="grid gap-3 sm:grid-cols-2">
                        <div className="rounded-lg border border-zinc-800 bg-zinc-950/70 p-4">
                            <p className="text-sm font-medium text-white">Close readiness</p>
                            <p className="mt-1 text-sm text-zinc-400">
                                Reconciliation and task status stay visible in one operating view.
                            </p>
                        </div>
                        <div className="rounded-lg border border-zinc-800 bg-zinc-950/70 p-4">
                            <p className="text-sm font-medium text-white">Safer handoffs</p>
                            <p className="mt-1 text-sm text-zinc-400">
                                Workspace context follows the user instead of relying on manual IDs.
                            </p>
                        </div>
                    </div>
                </div>

                <div className="mt-8 overflow-hidden rounded-lg border border-zinc-800 bg-zinc-950/80 p-4">
                    <Image
                        src="/globe.svg"
                        alt="Infinite Matters workspace illustration"
                        width={320}
                        height={160}
                        className="h-40 w-full object-contain opacity-90"
                    />
                </div>
            </section>

            <section className="p-8 md:p-10">
                <form
                    onSubmit={handleSubmit}
                    className="mx-auto flex h-full max-w-md flex-col justify-center space-y-5"
                >
                    <div className="space-y-2">
                        <h2 className="text-3xl font-semibold text-white">Sign in</h2>
                        <p className="text-sm text-zinc-400">
                            Use your Infinite Matters account to open your workspace.
                        </p>
                    </div>

                    {error ? (
                        <div className="rounded-md border border-rose-500/40 bg-rose-500/10 p-3 text-sm text-rose-200">
                            {error}
                        </div>
                    ) : null}

                    <div className="space-y-2">
                        <label className="text-sm font-medium text-zinc-300" htmlFor="email">
                            Email
                        </label>
                        <input
                            id="email"
                            className="w-full rounded-md border border-zinc-800 bg-zinc-950 px-3 py-3 text-white outline-none transition placeholder:text-zinc-600 focus:border-emerald-400"
                            type="email"
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
                            className="w-full rounded-md border border-zinc-800 bg-zinc-950 px-3 py-3 text-white outline-none transition placeholder:text-zinc-600 focus:border-emerald-400"
                            type="password"
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
