import Image from "next/image";
import Link from "next/link";

type BrandVariant = "primary" | "alternate" | "stacked" | "icon" | "app" | "favicon";

const variantMap: Record<BrandVariant, { src: string; width: number; height: number }> = {
    primary: { src: "/brand/infinite-matters-primary-lockup.svg", width: 760, height: 220 },
    alternate: { src: "/brand/infinite-matters-alternate-lockup.svg", width: 560, height: 160 },
    stacked: { src: "/brand/infinite-matters-stacked-lockup.svg", width: 280, height: 260 },
    icon: { src: "/brand/infinite-matters-icon.svg", width: 128, height: 128 },
    app: { src: "/brand/infinite-matters-app-icon.svg", width: 256, height: 256 },
    favicon: { src: "/brand/infinite-matters-favicon.svg", width: 96, height: 96 },
};

type BrandLogoProps = {
    variant?: BrandVariant;
    className?: string;
    alt?: string;
    priority?: boolean;
};

export function BrandLogo({
    variant = "alternate",
    className,
    alt = "Infinite Matters logo",
    priority = false,
}: BrandLogoProps) {
    const asset = variantMap[variant];

    return (
        <Image
            src={asset.src}
            alt={alt}
            width={asset.width}
            height={asset.height}
            priority={priority}
            className={className}
        />
    );
}

export function BrandLink({
    href = "/dashboard",
    variant = "alternate",
    className,
}: {
    href?: string;
    variant?: BrandVariant;
    className?: string;
}) {
    return (
        <Link href={href} className={className}>
            <BrandLogo variant={variant} />
        </Link>
    );
}
