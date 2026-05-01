package com.infinitematters.bookkeeping.web.dto;

import com.infinitematters.bookkeeping.notifications.CloseControlDisposition;

import java.time.LocalDate;

public record WorkflowTaskActionRequest(String note, CloseControlDisposition disposition, LocalDate nextTouchOn) {
}
