import Link from "next/link";
import { BrandLogo } from "@/components/brand-logo";
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
                        <div className="space-y-2">
                            <BrandLogo
                                variant="brandmarkWordmarkReverse"
                                className="h-10 w-auto"
                                alt="Infinite Matters"
                                priority
                            />
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
                <aside className="hidden w-72 border-r border-white/10 bg-[linear-gradient(180deg,rgba(8,20,31,0.84)_0%,rgba(6,10,12,0.92)_100%)] px-6 py-8 backdrop-blur lg:flex lg:flex-col">
                    <Link href="/dashboard" className="space-y-4">
                        <BrandLogo
                            variant="reverseWhite"
                            className="h-auto w-44"
                            alt="Infinite Matters"
                            priority
                        />
                        <h2 className="text-2xl font-semibold text-white">
                            Books that stay moving
                        </h2>
                        <p className="text-sm text-zinc-400">
                            Keep close readiness, review work, and reconciliations in one place.
                        </p>
                    </Link>

                    <div className="mt-8 rounded-lg border border-white/10 bg-white/[0.04] p-4 shadow-[0_12px_30px_rgba(0,0,0,0.18)]">
                        <WorkspaceSwitcher />
                    </div>
                    <div className="mt-6">
                        <WorkspaceTrustPanel />
                    </div>
                    <div className="mt-6 rounded-lg border border-white/10 bg-white/[0.04] p-3 shadow-[0_12px_30px_rgba(0,0,0,0.18)]">
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
