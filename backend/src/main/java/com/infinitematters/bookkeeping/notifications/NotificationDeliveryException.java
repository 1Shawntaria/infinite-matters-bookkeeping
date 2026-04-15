package com.infinitematters.bookkeeping.notifications;

public class NotificationDeliveryException extends RuntimeException {
    private final boolean retryable;
    private final String failureCode;

    public NotificationDeliveryException(String message, boolean retryable, String failureCode) {
        super(message);
        this.retryable = retryable;
        this.failureCode = failureCode;
    }

    public boolean retryable() {
        return retryable;
    }

    public String failureCode() {
        return failureCode;
    }
}
