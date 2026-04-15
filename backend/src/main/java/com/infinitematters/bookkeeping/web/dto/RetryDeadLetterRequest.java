package com.infinitematters.bookkeeping.web.dto;

public record RetryDeadLetterRequest(String recipientEmail, String note) {
}
