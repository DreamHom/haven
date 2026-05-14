package com.dreamhomes.haven.user.account.controller;

import com.dreamhomes.haven.auth.JwtPrincipal;
import com.dreamhomes.haven.auth.dto.ChangeMyPasswordRequest;
import com.dreamhomes.haven.auth.dto.MeResponse;
import com.dreamhomes.haven.auth.dto.UpdateMyAgentProfileRequest;
import com.dreamhomes.haven.auth.dto.UpdateMyProfileRequest;
import com.dreamhomes.haven.user.dto.PrivateUserProfile;
import com.dreamhomes.haven.user.dto.UserCredentials;
import com.dreamhomes.haven.user.exception.UserNotFoundException;
import com.dreamhomes.haven.user.service.UserAccountService;
import com.dreamhomes.haven.user.service.UserCredentialsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Self-service "current user" surface — identity bootstrap + private settings reads + writes.
 *
 * <ul>
 *   <li>{@code GET /api/me} — lightweight identity for app-boot pings. Returns
 *       {@link MeResponse} so internal fields like {@code tokenVersion} stay private.</li>
 *   <li>{@code GET /api/me/profile} — full settings preload ({@link PrivateUserProfile}):
 *       phone, license, agency, badges. Heavier; called only when the settings page opens.</li>
 *   <li>{@code PATCH /api/me} — partial update of email/fullName/displayName/phone.</li>
 *   <li>{@code POST /api/me/password} — password change with session revocation.</li>
 *   <li>{@code PATCH /api/me/agent-profile} — agent-only license + agency edits.</li>
 * </ul>
 *
 * <p>Identity always comes from the JWT subject; no endpoint accepts a userId in path or body.
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "Auth")
public class MeController {

    private final UserCredentialsService userCredentialsService;
    private final UserAccountService userAccountService;

    @Operation(
            summary = "Identify the current authenticated user",
            description = """
                    Returns the authenticated user's id, email, name, and role. Useful for the \
                    frontend on app boot to confirm a stored JWT is still valid and to display \
                    the user's role + name without a second profile call.

                    Does one DB read to pull `fullName`; not a hot-path call (frontend caches \
                    the response for the session).
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200",
                    description = "Authenticated user identity.",
                    content = @Content(
                            schema = @Schema(implementation = MeResponse.class),
                            examples = @ExampleObject(name = "OwnerIdentity", value = """
                                    { "userId": 7, "email": "amaka@gmail.com",
                                      "fullName": "Amaka Okafor", "role": "OWNER" }
                                    """))),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthenticated")
    })
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/api/me")
    public MeResponse me(@AuthenticationPrincipal JwtPrincipal principal) {
        UserCredentials creds = userCredentialsService.loadById(principal.userId())
                .orElseThrow(() -> new UserNotFoundException(principal.userId()));
        return new MeResponse(creds.id(), creds.email(), creds.fullName(), creds.role());
    }

    @Operation(
            summary = "Read the current user's account settings profile",
            description = """
                    Returns the authenticated user's editable account fields, including \
                    private identity data such as email, phone, and agent-license details.
                    This is the companion read endpoint a settings page needs before it can \
                    PATCH any of the write routes under `/api/me`.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200",
                    description = "Private account profile returned.",
                    content = @Content(
                            schema = @Schema(implementation = PrivateUserProfile.class),
                            examples = @ExampleObject(name = "AgentSettings", value = """
                                    { "userId": 23, "email": "agent@example.com",
                                      "fullName": "Emeka Okonkwo", "displayName": "Emeka",
                                      "phone": "+2348012345678", "role": "AGENT",
                                      "identityVerifiedAt": "2026-04-12T10:00:00Z",
                                      "agentCredentialVerifiedAt": "2026-04-13T11:00:00Z",
                                      "licenseNumber": "RC-67890",
                                      "agency": "Lekki Realty Co.",
                                      "suspended": false,
                                      "joinedAt": "2026-01-02T09:00:00Z" }
                                    """))),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthenticated")
    })
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/api/me/profile")
    public PrivateUserProfile myProfile(@AuthenticationPrincipal JwtPrincipal principal) {
        return userAccountService.findMyProfile(principal.userId());
    }

    @Operation(
            summary = "Update the current user's basic account fields",
            description = """
                    Partially updates the authenticated user's own profile basics. Identity \
                    always comes from the JWT subject; there is no userId path or body field, \
                    so callers cannot edit another account.

                    Email update is currently a direct write in this codebase. That is a \
                    deliberate temporary shortcut until email-delivery infrastructure exists \
                    for a proper verify-the-new-address flow.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Profile updated.",
                    content = @Content(schema = @Schema(implementation = PrivateUserProfile.class))),
            @ApiResponse(responseCode = "400", ref = "#/components/responses/ValidationFailed"),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthenticated"),
            @ApiResponse(responseCode = "409", ref = "#/components/responses/Conflict")
    })
    @SecurityRequirement(name = "bearerAuth")
    @PatchMapping("/api/me")
    public PrivateUserProfile updateMyProfile(@AuthenticationPrincipal JwtPrincipal principal,
                                            @Valid @RequestBody UpdateMyProfileRequest request) {
        return userAccountService.updateMyProfile(
                principal.userId(),
                request.email(),
                request.fullName(),
                request.displayName(),
                request.phone());
    }

    @Operation(
            summary = "Change the current user's password",
            description = """
                    Requires the current password for re-authentication, then stores the new \
                    password hash and bumps `tokenVersion` so every previously-issued JWT for \
                    the account is rejected on its next request.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Password changed; other sessions invalidated."),
            @ApiResponse(responseCode = "400", ref = "#/components/responses/ValidationFailed"),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthenticated"),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden")
    })
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/api/me/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@AuthenticationPrincipal JwtPrincipal principal,
                               @Valid @RequestBody ChangeMyPasswordRequest request) {
        userAccountService.changePassword(principal.userId(),
                request.currentPassword(),
                request.newPassword());
    }

    @Operation(
            summary = "Update the current agent's agent-profile fields",
            description = """
                    Agent-only settings endpoint. Supports license-renewal style edits **and** \
                    the four public-discovery fields (`serviceAreas`, `languages`, \
                    `specializationTags`, `feeSchedule`) that surface on the agent's public \
                    profile per PRD §4.2. If the license number changes, \
                    `credentialVerifiedAt` is cleared so the new credential must be re-verified.

                    **Partial-update semantics**: omit a field to leave its current value alone. \
                    For the array fields, send `[]` to clear all entries (distinct from `null` = \
                    no change). `feeSchedule` is normalised — a blank string after trim is stored \
                    as `null`, matching the `agency` behaviour.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Agent profile updated.",
                    content = @Content(schema = @Schema(implementation = PrivateUserProfile.class))),
            @ApiResponse(responseCode = "400", ref = "#/components/responses/ValidationFailed"),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthenticated"),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden"),
            @ApiResponse(responseCode = "409", ref = "#/components/responses/Conflict")
    })
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("hasRole('AGENT')")
    @PatchMapping("/api/me/agent-profile")
    public PrivateUserProfile updateMyAgentProfile(@AuthenticationPrincipal JwtPrincipal principal,
                                                 @Valid @RequestBody UpdateMyAgentProfileRequest request) {
        return userAccountService.updateMyAgentProfile(principal.userId(), request);
    }
}
