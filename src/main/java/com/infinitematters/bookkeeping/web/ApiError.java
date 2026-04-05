package com.infinitematters.bookkeeping.web;

import java.util.List;
import java.time.Instant;

public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        String requestId,
        List<String> details) {

    public ApiError(Instant timestamp,
                    int status,
                    String error,
                    String message,
                    String path,
                    String requestId) {
        this(timestamp, status, error, message, path, requestId, List.of());
    }
}
