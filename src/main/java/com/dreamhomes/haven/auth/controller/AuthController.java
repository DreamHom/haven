package com.dreamhomes.haven.auth.controller;

import com.dreamhomes.haven.auth.JwtPrincipal;
import com.dreamhomes.haven.auth.cookie.JwtCookieService;
import com.dreamhomes.haven.auth.dto.ForgotPasswordRequest;
import com.dreamhomes.haven.auth.dto.ForgotPasswordResponse;
import com.dreamhomes.haven.auth.dto.LoginCommand;
import com.dreamhomes.haven.auth.dto.LoginRequest;
import com.dreamhomes.haven.auth.dto.LoginResponse;
import com.dreamhomes.haven.auth.dto.RegisterCommand;
import com.dreamhomes.haven.auth.dto.RegisterRequest;
import com.dreamhomes.haven.auth.dto.ResetPasswordRequest;
import com.dreamhomes.haven.auth.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Auth")
public class AuthController {

    private final AuthService authService;
    private final com.dreamhomes.haven.auth.passwordreset.PasswordResetService passwordResetService;
    private final JwtCookieService jwtCookieService;

    @Operation(
            summary = "Register a new account",
            description = """
                    Creates a new user record with the requested role. The role is part of the \
                    request body — pick one of:

                    - `OWNER` — solo landlord (Amaka) or developer (Biodun).
                    - `AGENT` — must also provide `licenseNumber`; an `AgentProfile` row is \
                      created in the same transaction.
                    - `APPLICANT` — Temi's path; rents or buys.

                    Email is normalised before storage. Password must satisfy the strict \
                    validators (length, common-password blocklist).

                    **Always returns 202 Accepted with no body**, regardless of whether the \
                    email was already registered. This is deliberate — the API does not \
                    confirm or deny that an email exists in the system, which prevents \
                    enumeration attacks. Validation failures (400) and rate limiting (429) \
                    still surface as their normal status codes.

                    No JWT is returned by this endpoint — call `POST /auth/login` next to get \
                    a token. This path is rate-limited per-IP via bucket4j; aggressive callers \
                    receive 429.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "202",
                    description = "Request accepted. The response is identical whether the email"
                            + " was newly registered or already taken — that is the anti-enumeration"
                            + " contract."),
            @ApiResponse(responseCode = "400", ref = "#/components/responses/ValidationFailed"),
            @ApiResponse(responseCode = "429", ref = "#/components/responses/RateLimited")
    })
    @SecurityRequirements // public — opt out of bearerAuth
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public com.dreamhomes.haven.auth.dto.RegisterAcceptedResponse register(
            @Valid @RequestBody RegisterRequest request) {
        authService.register(new RegisterCommand(
                request.email(), request.password(), request.fullName(),
                request.displayName(), request.phone(),
                request.role(), request.licenseNumber()));
        return com.dreamhomes.haven.auth.dto.RegisterAcceptedResponse.DEFAULT;
    }

    @Operation(
            summary = "Log in and receive a JWT",
            description = """
                    Verifies email + password, then mints a stateless JWT containing \
                    `userId`, `role`, and `tokenVersion`. The JWT expiry is configured \
                    server-side (default 1h). Subsequent authenticated calls send the JWT \
                    as `Authorization: Bearer <token>`.

                    When `haven.auth.jwt-cookie.enabled` is true, the same JWT is also set in an \
                    httpOnly `SameSite=Lax` cookie so first-party browser apps can omit localStorage.

                    A suspended account (where `users.suspended_at` is set by an admin) \
                    cannot log in — the request returns 403 with a clear reason. Same \
                    for an account whose `tokenVersion` was bumped out from under any \
                    cached client.

                    This path is rate-limited per-IP. Brute-force-style attempts are \
                    throttled before they reach the password verifier.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200",
                    description = "Login successful — JWT issued.",
                    content = @Content(
                            schema = @Schema(implementation = LoginResponse.class),
                            examples = @ExampleObject(name = "TokenIssued", value = """
                                    { "token": "eyJhbGciOiJSUzI1NiJ9...<truncated>",
                                      "tokenType": "Bearer", "expiresInSeconds": 3600,
                                      "userId": 7, "role": "OWNER",
                                      "fullName": "Amaka Okafor" }
                                    """))),
            @ApiResponse(responseCode = "400", ref = "#/components/responses/ValidationFailed"),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthenticated"),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden"),
            @ApiResponse(responseCode = "429", ref = "#/components/responses/RateLimited")
    })
    @SecurityRequirements // public
    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request,
                               jakarta.servlet.http.HttpServletResponse httpResponse) {
        com.dreamhomes.haven.auth.dto.LoginResult result =
                authService.login(new LoginCommand(request.email(), request.password()));
        jwtCookieService.addTokenCookie(httpResponse, result.token());
        return new LoginResponse(result.token(), "Bearer", result.expiresInSeconds(),
                result.userId(), result.role(), result.fullName());
    }

    @Operation(
            summary = "Request a password reset",
            description = """
                    Always returns 202 with `accepted: true` — the response does not reveal \
                    whether the email exists. When a matching active account exists, a \
                    single-use reset token is stored (hashed). Email delivery is not wired \
                    yet; in non-production test profiles `debugResetToken` may echo the raw \
                    token when `haven.auth.debug-return-reset-token` is true (same value is \
                    also logged at WARN on the server).
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "Anti-enumeration ack."),
            @ApiResponse(responseCode = "400", ref = "#/components/responses/ValidationFailed"),
            @ApiResponse(responseCode = "429", ref = "#/components/responses/RateLimited")
    })
    @SecurityRequirements
    @PostMapping("/forgot-password")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ForgotPasswordResponse forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        return new ForgotPasswordResponse(true,
                passwordResetService.requestReset(request.email()).orElse(null));
    }

    @Operation(
            summary = "Complete password reset with token",
            description = """
                    Consumes a reset token from the forgot-password flow, sets a new password, \
                    and bumps `tokenVersion` so outstanding JWTs are revoked.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Password updated."),
            @ApiResponse(responseCode = "400", ref = "#/components/responses/ValidationFailed"),
            @ApiResponse(responseCode = "429", ref = "#/components/responses/RateLimited")
    })
    @SecurityRequirements
    @PostMapping("/reset-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetPassword(@Valid @RequestBody ResetPasswordRequest request,
                              jakarta.servlet.http.HttpServletResponse httpResponse) {
        passwordResetService.resetWithToken(request.token(), request.newPassword());
        jwtCookieService.clearTokenCookie(httpResponse);
    }

    @Operation(
            summary = "Log out the current user",
            description = """
                    Two scopes, selected via `?scope=`:

                    - `device` (default) — adds the current JWT's `jti` to the blocklist so
                      that one specific token is rejected on the next request. Other tokens
                      this user issued (other browsers, the mobile app) keep working.
                      Persona audit (Amaka, Temi): "logout the laptop I'm sitting at, not
                      every device I own".
                    - `all` — bumps the user's `tokenVersion`, invalidating every JWT for
                      this account on the next request. The right choice for "lost device"
                      or "ended a session on a shared computer".

                    When JWT cookies are enabled, the httpOnly session cookie is cleared as well.

                    Returns 204 No Content on success.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Logged out."),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthenticated")
    })
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@AuthenticationPrincipal JwtPrincipal principal,
                       @org.springframework.web.bind.annotation.RequestParam(name = "scope", defaultValue = "device")
                       String scope,
                       jakarta.servlet.http.HttpServletRequest request,
                       jakarta.servlet.http.HttpServletResponse response) {
        String authorization = request.getHeader("Authorization");
        if ((authorization == null || authorization.isBlank()) && jwtCookieService.isEnabled()) {
            authorization = jwtCookieService.readToken(request)
                    .map(t -> "Bearer " + t)
                    .orElse(null);
        }
        switch (scope.toLowerCase(java.util.Locale.ROOT)) {
            case "all" -> authService.logout(principal.userId());
            case "device" -> authService.logoutDevice(principal.userId(), authorization);
            default -> throw new IllegalArgumentException(
                    "Unknown logout scope: " + scope + " (expected device or all)");
        }
        jwtCookieService.clearTokenCookie(response);
    }
}
