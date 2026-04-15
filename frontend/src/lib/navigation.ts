export function mapBackendActionPathToFrontend(path?: string | null): string {
    if (!path) return "/dashboard";

    switch (path) {
        case "/workflows/inbox":
            return "/review-queue";
        case "/reconciliations":
            return "/reconciliation";
        case "/transactions":
            return "/transactions";
        default:
            return path;
    }
}