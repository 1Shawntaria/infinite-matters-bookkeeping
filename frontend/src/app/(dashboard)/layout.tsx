import Link from "next/link";
import { DashboardNav } from "@/components/dashboard-nav";
import { SignOutButton } from "@/components/sign-out-button";
import { WorkspaceSwitcher } from "@/components/workspace-switcher";

export default function DashboardLayout({
                                            children,
                                        }: {
    children: React.ReactNode;
}) {
    return (
        <div className="min-h-screen bg-[linear-gradient(180deg,#101312_0%,#070808_100%)] text-white">
            <div className="border-b border-zinc-900/80 bg-black/60 backdrop-blur lg:hidden">
                <div className="mx-auto flex max-w-7xl flex-col gap-4 px-4 py-4">
                    <div className="flex items-center justify-between gap-4">
                        <div>
                            <p className="text-xs uppercase tracking-[0.18em] text-emerald-300">
                                Infinite Matters
                            </p>
                            <p className="text-sm text-zinc-400">
                                Bookkeeping operations workspace
                            </p>
                        </div>
                        <SignOutButton compact />
                    </div>

                    <WorkspaceSwitcher />
                    <DashboardNav orientation="row" />
                </div>
            </div>

            <div className="mx-auto flex min-h-screen max-w-7xl">
                <aside className="hidden w-72 border-r border-zinc-900/80 bg-black/50 px-6 py-8 backdrop-blur lg:flex lg:flex-col">
                    <Link href="/dashboard" className="space-y-2">
                        <p className="text-xs uppercase tracking-[0.22em] text-emerald-300">
                            Infinite Matters
                        </p>
                        <h2 className="text-2xl font-semibold text-white">
                            Books that stay moving
                        </h2>
                        <p className="text-sm text-zinc-400">
                            Keep close readiness, review work, and reconciliations in one place.
                        </p>
                    </Link>

                    <WorkspaceSwitcher />
                    <div className="mt-6">
                        <DashboardNav />
                    </div>

                    <div className="mt-auto">
                        <SignOutButton />
                    </div>
                </aside>

                <main className="min-w-0 flex-1">{children}</main>
            </div>
        </div>
    );
}
