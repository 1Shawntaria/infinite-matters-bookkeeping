package com.infinitematters.bookkeeping.web.dto;

import com.infinitematters.bookkeeping.notifications.CloseControlDisposition;

public record WorkflowTaskActionRequest(String note, CloseControlDisposition disposition) {
}
