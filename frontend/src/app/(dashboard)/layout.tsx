import Link from "next/link";

export default function DashboardLayout({
                                            children,
                                        }: {
    children: React.ReactNode;
}) {
    return (
        <div className="min-h-screen bg-black text-white">
            <div className="flex min-h-screen">
                <aside className="hidden w-64 border-r border-zinc-800 bg-zinc-950 p-6 lg:block">
                    <h2 className="text-lg font-semibold text-white">Infinite Matters</h2>

                    <nav className="mt-6 space-y-3 text-sm text-zinc-400">
                        <Link href="/dashboard" className="block hover:text-white">
                            Dashboard
                        </Link>
                        <Link href="/review-queue" className="block hover:text-white">
                            Review Queue
                        </Link>
                        <Link href="/reconciliation" className="block hover:text-white">
                            Reconciliation
                        </Link>
                    </nav>
                </aside>

                <main className="flex-1 bg-black">{children}</main>
            </div>
        </div>
    );
}