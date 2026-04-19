"use client";

import { useRouter } from "next/navigation";
import { logout } from "@/lib/api/auth";
import { clearAuthSession } from "@/lib/auth/session";

export function SignOutButton() {
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
            className="mt-8 rounded-md border border-zinc-700 px-3 py-2 text-left text-sm text-zinc-400 hover:text-white"
        >
            Sign out
        </button>
    );
}
