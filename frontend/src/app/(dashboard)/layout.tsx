import Link from "next/link";
import { DashboardNav } from "@/components/dashboard-nav";
import { SignOutButton } from "@/components/sign-out-button";
import { WorkspaceTrustPanel } from "@/components/workspace-trust-panel";
import { WorkspaceSwitcher } from "@/components/workspace-switcher";

export default function DashboardLayout({
                                            children,
                                        }: {
    children: React.ReactNode;
}) {
    return (
        <div className="min-h-screen bg-[linear-gradient(180deg,#101312_0%,#070808_100%)] text-white">
            <div className="border-b border-white/10 bg-black/55 backdrop-blur lg:hidden">
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
                    <WorkspaceTrustPanel />
                    <DashboardNav orientation="row" />
                </div>
            </div>

            <div className="mx-auto flex min-h-screen max-w-7xl">
                <aside className="hidden w-72 border-r border-white/10 bg-black/45 px-6 py-8 backdrop-blur lg:flex lg:flex-col">
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

                    <div className="mt-8 rounded-lg border border-white/10 bg-white/[0.03] p-4">
                        <WorkspaceSwitcher />
                    </div>
                    <div className="mt-6">
                        <WorkspaceTrustPanel />
                    </div>
                    <div className="mt-6 rounded-lg border border-white/10 bg-white/[0.03] p-3">
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
