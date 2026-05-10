package com.dreamhomes.haven.auth.controller;

import com.dreamhomes.haven.auth.JwtPrincipal;
import com.dreamhomes.haven.auth.dto.LoginCommand;
import com.dreamhomes.haven.auth.dto.LoginRequest;
import com.dreamhomes.haven.auth.dto.LoginResponse;
import com.dreamhomes.haven.auth.dto.RegisterCommand;
import com.dreamhomes.haven.auth.dto.RegisterRequest;
import com.dreamhomes.haven.auth.dto.UserResponse;
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

    @Operation(
            summary = "Register a new account",
            description = """
                    Creates a new user record with the requested role and returns the user's \
                    public-facing fields. The role is part of the request body — pick one of:

                    - `OWNER` — solo landlord (Amaka) or developer (Biodun).
                    - `AGENT` — must also provide `licenseNumber`; an `AgentProfile` row is \
                      created in the same transaction.
                    - `APPLICANT` — Temi's path; rents or buys.

                    Email is normalised + uniqueness-checked. Password must satisfy the strict \
                    validators (length, common-password blocklist).

                    No JWT is returned by this endpoint — call `POST /auth/login` next to get \
                    a token. This path is rate-limited per-IP via bucket4j; aggressive callers \
                    receive 429.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201",
                    description = "Account created.",
                    content = @Content(
                            schema = @Schema(implementation = UserResponse.class),
                            examples = @ExampleObject(name = "AmakaRegistered", value = """
                                    { "id": 7, "email": "amaka@example.com", "fullName": "Amaka Okafor",
                                      "role": "OWNER", "phone": "+2348012345678",
                                      "createdAt": "2026-05-10T08:30:00Z" }
                                    """))),
            @ApiResponse(responseCode = "400", ref = "#/components/responses/ValidationFailed"),
            @ApiResponse(responseCode = "409", ref = "#/components/responses/Conflict"),
            @ApiResponse(responseCode = "429", ref = "#/components/responses/RateLimited")
    })
    @SecurityRequirements // public — opt out of bearerAuth
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(new RegisterCommand(
                request.email(), request.password(), request.fullName(), request.phone(),
                request.role(), request.licenseNumber()));
    }

    @Operation(
            summary = "Log in and receive a JWT",
            description = """
                    Verifies email + password, then mints a stateless JWT containing \
                    `userId`, `role`, and `tokenVersion`. The JWT expiry is configured \
                    server-side (default 1h). Subsequent authenticated calls send the JWT \
                    as `Authorization: Bearer <token>`.

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
                                    { "token": "eyJhbGciOiJIUzI1NiJ9...<truncated>",
                                      "tokenType": "Bearer", "expiresInSeconds": 3600 }
                                    """))),
            @ApiResponse(responseCode = "400", ref = "#/components/responses/ValidationFailed"),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthenticated"),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden"),
            @ApiResponse(responseCode = "429", ref = "#/components/responses/RateLimited")
    })
    @SecurityRequirements // public
    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return new LoginResponse(authService.login(new LoginCommand(request.email(), request.password())));
    }

    @Operation(
            summary = "Log out the current user",
            description = """
                    Bumps the user's `tokenVersion`, which invalidates **all** outstanding \
                    JWTs for the account on their next request — not just the one used to \
                    call this endpoint. This is the right behaviour for a logout-everywhere \
                    flow (lost device, ended session on shared computer).

                    Returns 204 No Content on success.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Logged out; tokenVersion bumped."),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthenticated")
    })
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@AuthenticationPrincipal JwtPrincipal principal) {
        authService.logout(principal.userId());
    }
}
