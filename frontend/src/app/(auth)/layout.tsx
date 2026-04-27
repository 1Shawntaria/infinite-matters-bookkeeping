export default function AuthLayout({
                                       children,
                                   }: {
    children: React.ReactNode;
}) {
    return (
        <div className="flex min-h-screen items-center justify-center bg-[radial-gradient(circle_at_top,#153038_0%,#0c1215_38%,#060708_100%)] px-4 py-10 text-white">
            {children}
        </div>
    );
}
