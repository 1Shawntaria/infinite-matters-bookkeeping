import { ReactNode } from "react";

type PageHeroProps = {
    eyebrow: string;
    title: string;
    description: string;
    aside?: ReactNode;
    children?: ReactNode;
};

export function PageHero({
    eyebrow,
    title,
    description,
    aside,
    children,
}: PageHeroProps) {
    return (
        <section className="overflow-hidden rounded-lg border border-white/10 bg-[linear-gradient(135deg,rgba(15,23,20,0.96),rgba(10,12,12,0.92)_58%,rgba(32,84,67,0.48))] shadow-[0_24px_80px_rgba(0,0,0,0.24)]">
            <div className="flex flex-col gap-6 px-5 py-6 sm:px-6 lg:flex-row lg:items-end lg:justify-between lg:px-8 lg:py-8">
                <div className="max-w-3xl space-y-3">
                    <p className="text-xs font-medium uppercase tracking-[0.22em] text-emerald-300">
                        {eyebrow}
                    </p>
                    <div className="space-y-2">
                        <h1 className="text-3xl font-semibold text-white sm:text-4xl">
                            {title}
                        </h1>
                        <p className="max-w-2xl text-sm leading-6 text-zinc-300 sm:text-base">
                            {description}
                        </p>
                    </div>
                    {children ? <div className="pt-2">{children}</div> : null}
                </div>

                {aside ? <div className="lg:max-w-sm">{aside}</div> : null}
            </div>
        </section>
    );
}

type SummaryMetricProps = {
    label: string;
    value: string;
    tone?: "default" | "success" | "warning";
    detail?: string;
};

export function SummaryMetric({
    label,
    value,
    tone = "default",
    detail,
}: SummaryMetricProps) {
    const valueTone =
        tone === "success"
            ? "text-emerald-300"
            : tone === "warning"
              ? "text-amber-200"
              : "text-white";

    return (
        <div className="rounded-lg border border-white/10 bg-white/[0.03] p-4 shadow-[inset_0_1px_0_rgba(255,255,255,0.03)]">
            <p className="text-xs font-medium uppercase tracking-[0.18em] text-zinc-500">
                {label}
            </p>
            <p className={["mt-3 text-2xl font-semibold", valueTone].join(" ")}>
                {value}
            </p>
            {detail ? <p className="mt-2 text-sm text-zinc-400">{detail}</p> : null}
        </div>
    );
}

type SectionBandProps = {
    eyebrow?: string;
    title: string;
    description?: string;
    actions?: ReactNode;
    children: ReactNode;
};

export function SectionBand({
    eyebrow,
    title,
    description,
    actions,
    children,
}: SectionBandProps) {
    return (
        <section className="rounded-lg border border-white/10 bg-black/30 px-5 py-5 shadow-[0_18px_50px_rgba(0,0,0,0.16)] backdrop-blur sm:px-6">
            <div className="flex flex-col gap-4 lg:flex-row lg:items-end lg:justify-between">
                <div className="space-y-1">
                    {eyebrow ? (
                        <p className="text-xs font-medium uppercase tracking-[0.18em] text-zinc-500">
                            {eyebrow}
                        </p>
                    ) : null}
                    <h2 className="text-xl font-semibold text-white">{title}</h2>
                    {description ? (
                        <p className="max-w-3xl text-sm leading-6 text-zinc-400">
                            {description}
                        </p>
                    ) : null}
                </div>

                {actions ? <div className="shrink-0">{actions}</div> : null}
            </div>

            <div className="mt-5">{children}</div>
        </section>
    );
}

type StatusBannerProps = {
    tone: "error" | "success" | "muted";
    title: string;
    message: string;
};

export function StatusBanner({ tone, title, message }: StatusBannerProps) {
    const toneClasses =
        tone === "error"
            ? "border-rose-500/30 bg-rose-500/10 text-rose-100"
            : tone === "success"
              ? "border-emerald-500/30 bg-emerald-500/10 text-emerald-100"
              : "border-white/10 bg-white/[0.04] text-zinc-100";

    return (
        <div className={["rounded-lg border px-4 py-4", toneClasses].join(" ")}>
            <p className="text-sm font-medium">{title}</p>
            <p className="mt-1 text-sm text-current/80">{message}</p>
        </div>
    );
}

export function ProgressMeter({
    label,
    value,
    total,
    tone = "default",
}: {
    label: string;
    value: number;
    total: number;
    tone?: "default" | "success" | "warning";
}) {
    const safeTotal = total <= 0 ? 1 : total;
    const percentage = Math.max(0, Math.min(100, (value / safeTotal) * 100));
    const barTone =
        tone === "success"
            ? "bg-emerald-300"
            : tone === "warning"
              ? "bg-amber-300"
              : "bg-white";

    return (
        <div className="space-y-2">
            <div className="flex items-center justify-between gap-3 text-sm">
                <span className="text-zinc-300">{label}</span>
                <span className="text-zinc-500">
                    {value}/{total}
                </span>
            </div>
            <div className="h-2 overflow-hidden rounded-full bg-white/10">
                <div
                    className={["h-full rounded-full", barTone].join(" ")}
                    style={{ width: `${percentage}%` }}
                />
            </div>
        </div>
    );
}

export function NextStepsList({
    title,
    items,
}: {
    title: string;
    items: string[];
}) {
    return (
        <div className="rounded-lg border border-white/10 bg-white/[0.03] p-4">
            <h3 className="text-sm font-semibold text-white">{title}</h3>
            <div className="mt-3 space-y-3">
                {items.map((item, index) => (
                    <div key={item} className="flex gap-3">
                        <span className="flex h-6 w-6 shrink-0 items-center justify-center rounded-full border border-white/10 bg-white/[0.04] text-xs text-zinc-300">
                            {index + 1}
                        </span>
                        <p className="pt-0.5 text-sm text-zinc-400">{item}</p>
                    </div>
                ))}
            </div>
        </div>
    );
}

export function MiniBarChart({
    title,
    items,
}: {
    title: string;
    items: Array<{
        label: string;
        value: number;
        helper?: string;
    }>;
}) {
    const maxValue = Math.max(1, ...items.map((item) => item.value));

    return (
        <div className="rounded-lg border border-white/10 bg-white/[0.03] p-4">
            <h3 className="text-sm font-semibold text-white">{title}</h3>
            <div className="mt-4 space-y-4">
                {items.map((item) => (
                    <div key={item.label} className="space-y-2">
                        <div className="flex items-baseline justify-between gap-3">
                            <div>
                                <p className="text-sm text-zinc-200">{item.label}</p>
                                {item.helper ? (
                                    <p className="text-xs text-zinc-500">{item.helper}</p>
                                ) : null}
                            </div>
                            <p className="text-sm font-medium text-white">
                                {item.value.toLocaleString()}
                            </p>
                        </div>
                        <div className="h-2 overflow-hidden rounded-full bg-white/10">
                            <div
                                className="h-full rounded-full bg-emerald-300"
                                style={{ width: `${Math.max(8, (item.value / maxValue) * 100)}%` }}
                            />
                        </div>
                    </div>
                ))}
            </div>
        </div>
    );
}

export function LoadingPanel({
    title,
    message,
}: {
    title: string;
    message: string;
}) {
    return (
        <main className="p-4 sm:p-6 lg:p-8">
            <div className="rounded-lg border border-white/10 bg-black/30 px-5 py-8 backdrop-blur sm:px-6">
                <div className="h-3 w-28 rounded-full bg-white/8" />
                <div className="mt-4 h-10 w-56 rounded-full bg-white/10" />
                <div className="mt-3 h-4 w-full max-w-xl rounded-full bg-white/7" />
                <div className="mt-8 grid gap-4 md:grid-cols-3">
                    <div className="h-28 rounded-lg bg-white/6" />
                    <div className="h-28 rounded-lg bg-white/6" />
                    <div className="h-28 rounded-lg bg-white/6" />
                </div>
                <p className="mt-6 text-sm text-zinc-400">
                    <span className="font-medium text-zinc-200">{title}</span> {message}
                </p>
            </div>
        </main>
    );
}
