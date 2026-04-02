package com.infinitematters.bookkeeping.web;

import com.infinitematters.bookkeeping.audit.AuditEventSummary;
import com.infinitematters.bookkeeping.audit.AuditService;
import com.infinitematters.bookkeeping.security.AuthTokenService;
import com.infinitematters.bookkeeping.security.AuthRateLimitService;
import com.infinitematters.bookkeeping.security.RefreshTokenService;
import com.infinitematters.bookkeeping.security.RequestIdentityService;
import com.infinitematters.bookkeeping.users.AppUser;
import com.infinitematters.bookkeeping.users.PasswordResetService;
import com.infinitematters.bookkeeping.users.UserService;
import com.infinitematters.bookkeeping.notifications.NotificationService;
import com.infinitematters.bookkeeping.notifications.NotificationSummary;
import com.infinitematters.bookkeeping.web.dto.AuthSessionSummary;
import com.infinitematters.bookkeeping.web.dto.AuthTokenResponse;
import com.infinitematters.bookkeeping.web.dto.ForgotPasswordRequest;
import com.infinitematters.bookkeeping.web.dto.ForgotPasswordResponse;
import com.infinitematters.bookkeeping.web.dto.LoginRequest;
import com.infinitematters.bookkeeping.web.dto.LogoutRequest;
import com.infinitematters.bookkeeping.web.dto.RefreshTokenRequest;
import com.infinitematters.bookkeeping.web.dto.RevokeSessionRequest;
import com.infinitematters.bookkeeping.web.dto.ResetPasswordRequest;
import com.infinitematters.bookkeeping.web.dto.UserResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final UserService userService;
    private final AuthTokenService authTokenService;
    private final AuthRateLimitService authRateLimitService;
    private final RefreshTokenService refreshTokenService;
    private final PasswordResetService passwordResetService;
    private final NotificationService notificationService;
    private final RequestIdentityService requestIdentityService;
    private final AuditService auditService;
    private final boolean exposePasswordResetTokenResponse;

    public AuthController(UserService userService,
                          AuthTokenService authTokenService,
                          AuthRateLimitService authRateLimitService,
                          RefreshTokenService refreshTokenService,
                          PasswordResetService passwordResetService,
                          NotificationService notificationService,
                          RequestIdentityService requestIdentityService,
                          AuditService auditService,
                          @Value("${bookkeeping.auth.password-reset.expose-token-response:false}")
                          boolean exposePasswordResetTokenResponse) {
        this.userService = userService;
        this.authTokenService = authTokenService;
        this.authRateLimitService = authRateLimitService;
        this.refreshTokenService = refreshTokenService;
        this.passwordResetService = passwordResetService;
        this.notificationService = notificationService;
        this.requestIdentityService = requestIdentityService;
        this.auditService = auditService;
        this.exposePasswordResetTokenResponse = exposePasswordResetTokenResponse;
    }

    @PostMapping("/token")
    public AuthTokenResponse token(@Valid @RequestBody LoginRequest request) {
        authRateLimitService.checkAllowed("login", request.email());
        AppUser user;
        try {
            user = userService.authenticate(request.email(), request.password());
        } catch (RuntimeException exception) {
            authRateLimitService.recordFailure("login", request.email());
            userService.findByEmail(request.email())
                    .ifPresent(foundUser -> auditService.recordForUser(
                            foundUser.getId(),
                            null,
                            "AUTH_LOGIN_FAILED",
                            "app_user",
                            foundUser.getId().toString(),
                            "Invalid credentials supplied"));
            throw exception;
        }
        authRateLimitService.recordSuccess("login", request.email());
        auditService.recordForUser(user.getId(), null, "AUTH_LOGIN_SUCCEEDED",
                "app_user", user.getId().toString(), "User authenticated");
        return issueTokenPair(user, refreshTokenService.issue(user));
    }

    @PostMapping("/refresh")
    public AuthTokenResponse refresh(@Valid @RequestBody RefreshTokenRequest request) {
        RefreshTokenService.RotatedRefreshToken rotatedRefreshToken =
                refreshTokenService.rotate(request.refreshToken());
        return issueTokenPair(rotatedRefreshToken.user(), rotatedRefreshToken.refreshToken());
    }

    @PostMapping("/logout")
    public void logout(@Valid @RequestBody LogoutRequest request) {
        refreshTokenService.revoke(request.refreshToken());
    }

    @PostMapping("/forgot-password")
    public ForgotPasswordResponse forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        authRateLimitService.checkAllowed("password-reset", request.email());
        return userService.findByEmail(request.email())
                .flatMap(passwordResetService::issue)
                .map(token -> {
                    authRateLimitService.recordSuccess("password-reset", request.email());
                    return new ForgotPasswordResponse(
                            "RESET_TOKEN_ISSUED",
                            "EMAIL",
                            exposePasswordResetTokenResponse ? token.token() : null,
                            exposePasswordResetTokenResponse ? token.expiresAt() : null);
                })
                .orElseGet(() -> {
                    authRateLimitService.recordSuccess("password-reset", request.email());
                    auditService.record(null, "PASSWORD_RESET_REQUEST_IGNORED",
                            "email", request.email().toLowerCase(), "No matching user for password reset request");
                    return new ForgotPasswordResponse("RESET_TOKEN_ISSUED", "EMAIL", null, null);
                });
    }

    @PostMapping("/reset-password")
    public UserResponse resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authRateLimitService.checkAllowed("reset-password", request.token());
        AppUser user;
        try {
            user = passwordResetService.consume(request.token());
        } catch (RuntimeException exception) {
            authRateLimitService.recordFailure("reset-password", request.token());
            throw exception;
        }
        authRateLimitService.recordSuccess("reset-password", request.token());
        AppUser updatedUser = userService.updatePassword(user.getId(), request.newPassword());
        refreshTokenService.revokeAllSessionsForUser(updatedUser.getId(), "PASSWORD_RESET");
        auditService.recordForUser(updatedUser.getId(), null, "AUTH_PASSWORD_CHANGED",
                "app_user", updatedUser.getId().toString(), "Password updated through reset flow");
        notificationService.sendAuthNotification(
                updatedUser,
                com.infinitematters.bookkeeping.notifications.NotificationCategory.AUTH_SECURITY,
                com.infinitematters.bookkeeping.notifications.NotificationChannel.EMAIL,
                "Your password was changed successfully. If this was not you, reset your password again immediately.",
                "app_user",
                updatedUser.getId().toString());
        return UserResponse.from(updatedUser);
    }

    @GetMapping("/sessions")
    public List<AuthSessionSummary> sessions() {
        UUID userId = requestIdentityService.requireUserId();
        return refreshTokenService.listSessions(userId).stream()
                .map(AuthSessionSummary::from)
                .toList();
    }

    @PostMapping("/sessions/{sessionId}/revoke")
    public AuthSessionSummary revokeSession(@PathVariable UUID sessionId,
                                            @Valid @RequestBody RevokeSessionRequest request) {
        UUID userId = requestIdentityService.requireUserId();
        return AuthSessionSummary.from(refreshTokenService.revokeSession(sessionId, userId, request.reason()));
    }

    @GetMapping("/notifications")
    public List<NotificationSummary> authNotifications() {
        UUID userId = requestIdentityService.requireUserId();
        return notificationService.listForUser(userId);
    }

    @GetMapping("/activity")
    public List<AuditEventSummary> authActivity() {
        UUID userId = requestIdentityService.requireUserId();
        return auditService.listForCurrentUserSecurity(userId);
    }

    @GetMapping("/me")
    public UserResponse me() {
        return UserResponse.from(requestIdentityService.requireUser());
    }

    private AuthTokenResponse issueTokenPair(AppUser user,
                                             RefreshTokenService.IssuedRefreshToken refreshToken) {
        AuthTokenService.IssuedToken accessToken = authTokenService.issueToken(user);
        return new AuthTokenResponse(
                accessToken.value(),
                "Bearer",
                accessToken.expiresAt(),
                refreshToken.value(),
                refreshToken.sessionId(),
                refreshToken.expiresAt(),
                UserResponse.from(user));
    }
}
