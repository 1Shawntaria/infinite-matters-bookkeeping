"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";

const NAV_ITEMS = [
    { href: "/dashboard", label: "Dashboard" },
    { href: "/activity", label: "Activity" },
    { href: "/notifications", label: "Notifications" },
    { href: "/access", label: "Access" },
    { href: "/setup", label: "Setup & Import" },
    { href: "/review-queue", label: "Review Queue" },
    { href: "/reconciliation", label: "Reconciliation" },
];

type DashboardNavProps = {
    orientation?: "row" | "column";
};

export function DashboardNav({
    orientation = "column",
}: DashboardNavProps) {
    const pathname = usePathname();

    return (
        <nav
            className={
                orientation === "row"
                    ? "flex flex-wrap gap-2"
                    : "flex flex-col gap-2"
            }
        >
            {NAV_ITEMS.map((item) => {
                const active = pathname === item.href;

                return (
                    <Link
                        key={item.href}
                        href={item.href}
                        className={[
                            "rounded-md px-3 py-2 text-sm font-medium",
                            active
                                ? "bg-emerald-300 text-black shadow-[0_10px_28px_rgba(110,231,183,0.18)]"
                                : "text-zinc-400 hover:bg-white/[0.05] hover:text-white",
                        ].join(" ")}
                    >
                        {item.label}
                    </Link>
                );
            })}
        </nav>
    );
}
