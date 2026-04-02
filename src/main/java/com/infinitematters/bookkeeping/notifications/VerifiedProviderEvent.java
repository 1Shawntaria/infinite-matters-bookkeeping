package com.infinitematters.bookkeeping.notifications;

public record VerifiedProviderEvent(
        String verificationMethod,
        String verificationReference) {
}
