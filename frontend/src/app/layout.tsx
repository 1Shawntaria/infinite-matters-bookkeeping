import "./globals.css";
import { QueryProvider } from "@/providers/query-provider";

export const metadata = {
    title: "Infinite Matters",
    description: "Bookkeeping workflow platform",
};

export default function RootLayout({
                                       children,
                                   }: {
    children: React.ReactNode;
}) {
    return (
        <html lang="en">
        <body className="bg-black text-white">
        <QueryProvider>{children}</QueryProvider>
        </body>
        </html>
    );
}