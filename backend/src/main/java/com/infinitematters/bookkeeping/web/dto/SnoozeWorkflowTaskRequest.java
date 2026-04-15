package com.infinitematters.bookkeeping.web.dto;

import java.time.LocalDate;

public record SnoozeWorkflowTaskRequest(LocalDate snoozedUntil, String note) {
}
