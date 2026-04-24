"use client";

import { useRouter } from "next/navigation";
import { logout } from "@/lib/api/auth";
import { clearAuthSession } from "@/lib/auth/session";

export function SignOutButton({ compact = false }: { compact?: boolean }) {
    const router = useRouter();

    async function handleSignOut() {
        try {
            await logout();
        } finally {
            clearAuthSession();
            router.push("/login");
        }
    }

    return (
        <button
            type="button"
            onClick={handleSignOut}
            className={[
                compact
                    ? "rounded-md border border-zinc-800 px-3 py-2 text-sm text-zinc-300 hover:bg-zinc-950 hover:text-white"
                    : "mt-8 w-full rounded-md border border-zinc-800 px-3 py-2 text-left text-sm text-zinc-300 hover:bg-zinc-950 hover:text-white",
            ].join(" ")}
        >
            Sign out
        </button>
    );
}
