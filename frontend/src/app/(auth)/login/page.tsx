"use client";

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
        <main className="flex min-h-screen items-center justify-center px-4">
            <form
                onSubmit={handleSubmit}
                className="w-full max-w-sm space-y-4 rounded-xl border p-6 shadow-sm"
            >
                <div className="space-y-1">
                    <h1 className="text-2xl font-semibold">Infinite Matters</h1>
                    <p className="text-sm text-gray-600">Sign in to your workspace.</p>
                </div>

                {error ? (
                    <div className="rounded-md border border-red-200 bg-red-50 p-3 text-sm text-red-700">
                        {error}
                    </div>
                ) : null}

                <div className="space-y-2">
                    <label className="text-sm font-medium" htmlFor="email">
                        Email
                    </label>
                    <input
                        id="email"
                        className="w-full rounded-md border px-3 py-2 outline-none"
                        type="email"
                        value={email}
                        onChange={(e) => setEmail(e.target.value)}
                        placeholder="owner@acme.test"
                        required
                    />
                </div>

                <div className="space-y-2">
                    <label className="text-sm font-medium" htmlFor="password">
                        Password
                    </label>
                    <input
                        id="password"
                        className="w-full rounded-md border px-3 py-2 outline-none"
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
                    className="w-full rounded-md bg-black px-4 py-2 text-white disabled:opacity-50"
                >
                    {isSubmitting ? "Signing in..." : "Sign In"}
                </button>
            </form>
        </main>
    );
}
