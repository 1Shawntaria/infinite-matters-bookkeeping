"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";

const NAV_ITEMS = [
    { href: "/dashboard", label: "Dashboard" },
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
                            "rounded-md px-3 py-2 text-sm font-medium transition",
                            active
                                ? "bg-emerald-400 text-black"
                                : "text-zinc-400 hover:bg-zinc-900 hover:text-white",
                        ].join(" ")}
                    >
                        {item.label}
                    </Link>
                );
            })}
        </nav>
    );
}
