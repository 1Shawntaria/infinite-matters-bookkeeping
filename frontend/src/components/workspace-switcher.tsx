"use client";

import { useEffect, useState } from "react";
import { listOrganizations, OrganizationSummary } from "@/lib/api/auth";
import { getOrganizationId, setOrganizationId } from "@/lib/auth/session";

export function WorkspaceSwitcher() {
    const [organizations, setOrganizations] = useState<OrganizationSummary[]>([]);
    const [activeOrganizationId, setActiveOrganizationId] = useState(getOrganizationId);
    const [error, setError] = useState("");

    useEffect(() => {
        let mounted = true;

        listOrganizations()
            .then((result) => {
                if (!mounted) return;

                setOrganizations(result);

                const currentOrganizationId = getOrganizationId();
                const hasCurrentOrganization = result.some(
                    (organization) => organization.id === currentOrganizationId
                );

                if (!currentOrganizationId && result[0]) {
                    setOrganizationId(result[0].id);
                    setActiveOrganizationId(result[0].id);
                    return;
                }

                if (currentOrganizationId && !hasCurrentOrganization && result[0]) {
                    setOrganizationId(result[0].id);
                    setActiveOrganizationId(result[0].id);
                }
            })
            .catch((err: Error) => {
                if (!mounted) return;
                setError(err.message);
            });

        return () => {
            mounted = false;
        };
    }, []);

    function handleChange(event: React.ChangeEvent<HTMLSelectElement>) {
        const nextOrganizationId = event.target.value;
        setOrganizationId(nextOrganizationId);
        setActiveOrganizationId(nextOrganizationId);
        window.location.assign("/dashboard");
    }

    if (error) {
        return (
            <p className="mt-6 rounded-md border border-red-500/40 bg-red-500/10 p-3 text-xs text-red-200">
                Workspace unavailable.
            </p>
        );
    }

    if (organizations.length === 0) {
        return (
            <div className="mt-6 space-y-2">
                <p className="text-xs font-medium uppercase text-zinc-500">Workspace</p>
                <p className="rounded-md border border-zinc-800 bg-black px-3 py-2 text-sm text-zinc-500">
                    Loading...
                </p>
            </div>
        );
    }

    return (
        <label className="mt-6 block space-y-2">
            <span className="text-xs font-medium uppercase text-zinc-500">Workspace</span>
            <select
                value={activeOrganizationId}
                onChange={handleChange}
                className="w-full rounded-md border border-zinc-800 bg-black px-3 py-2 text-sm text-white outline-none"
            >
                {organizations.map((organization) => (
                    <option key={organization.id} value={organization.id}>
                        {organization.name}
                    </option>
                ))}
            </select>
        </label>
    );
}
