import Image from "next/image";
import Link from "next/link";

type BrandVariant =
    | "primary"
    | "alternate"
    | "stacked"
    | "icon"
    | "app"
    | "favicon"
    | "brandmarkWordmark"
    | "brandmarkWordmarkReverse"
    | "wordmarkOnly"
    | "monochromeBlack"
    | "reverseWhite";

const variantMap: Record<BrandVariant, { src: string; width: number; height: number }> = {
    primary: { src: "/brand/infinite-matters-primary-lockup.svg", width: 980, height: 270 },
    alternate: { src: "/brand/infinite-matters-alternate-lockup.svg", width: 900, height: 240 },
    stacked: { src: "/brand/infinite-matters-stacked-lockup.svg", width: 420, height: 420 },
    icon: { src: "/brand/infinite-matters-icon.svg", width: 220, height: 240 },
    app: { src: "/brand/infinite-matters-app-icon.svg", width: 256, height: 256 },
    favicon: { src: "/brand/infinite-matters-favicon.svg", width: 128, height: 128 },
    brandmarkWordmark: { src: "/brand/infinite-matters-brandmark-wordmark-horizontal.svg", width: 700, height: 170 },
    brandmarkWordmarkReverse: { src: "/brand/infinite-matters-brandmark-wordmark-reverse.svg", width: 560, height: 130 },
    wordmarkOnly: { src: "/brand/infinite-matters-wordmark-only.svg", width: 560, height: 130 },
    monochromeBlack: { src: "/brand/infinite-matters-monochrome-black.svg", width: 600, height: 170 },
    reverseWhite: { src: "/brand/infinite-matters-reverse-white.svg", width: 600, height: 170 },
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
