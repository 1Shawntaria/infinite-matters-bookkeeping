package com.infinitematters.bookkeeping.web.dto;

import jakarta.validation.constraints.Size;

public record AcceptInvitationRequest(
        @Size(max = 120) String fullName,
        @Size(min = 8, max = 120) String password) {
}
