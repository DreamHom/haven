package com.dreamhomes.haven.admin.controller;

import com.dreamhomes.haven.admin.dto.SuspendUserRequest;
import com.dreamhomes.haven.admin.service.AdminUserService;
import com.dreamhomes.haven.auth.JwtPrincipal;
import com.dreamhomes.haven.user.dto.UserAdminView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin")
public class AdminUserController {

    private final AdminUserService adminUserService;

    @Operation(
            summary = "Suspend a user account",
            description = """
                    Stamps `users.suspended_at` and **bumps `tokenVersion`**. The token-version \
                    bump invalidates every outstanding JWT for the user — they'll be logged \
                    out the moment they make their next request, regardless of token expiry.

                    **Defensive checks**:
                    - Cannot suspend yourself (CannotModerateSelfException → 403).
                    - Cannot suspend an already-suspended user (409).

                    **Side effects**:
                    - Audit log row written with the supplied reason.
                    - `tokenVersion` bump revokes the user's sessions.

                    **Reason**: required, non-empty.

                    **Role gate**: `ADMIN`.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200",
                    description = "User suspended.",
                    content = @Content(
                            schema = @Schema(implementation = UserAdminView.class),
                            examples = @ExampleObject(name = "SuspendedAgent", value = """
                                    { "id": 23, "email": "emeka@gmail.com", "fullName": "Emeka Okonkwo",
                                      "role": "AGENT",
                                      "suspendedAt": "2026-05-10T16:00:00Z",
                                      "createdAt": "2026-04-01T10:00:00Z" }
                                    """))),
            @ApiResponse(responseCode = "400", ref = "#/components/responses/ValidationFailed"),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthenticated"),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound"),
            @ApiResponse(responseCode = "409", ref = "#/components/responses/Conflict")
    })
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/{id}/suspend")
    public UserAdminView suspend(
            @AuthenticationPrincipal JwtPrincipal principal,
            @Parameter(description = "User ID to suspend.", example = "23")
            @PathVariable Long id,
            @Valid @RequestBody SuspendUserRequest request) {
        return adminUserService.suspend(principal.userId(), id, request.reason());
    }

    @Operation(
            summary = "Reactivate a suspended user account",
            description = """
                    Clears `users.suspended_at`. **`tokenVersion` is NOT re-bumped** — the \
                    suspend bump already invalidated every token; double-bumping would force \
                    a wasted re-login on a fresh token issued post-reactivation.

                    **Defensive check**: cannot reactivate a non-suspended user (409).

                    **Side effects**: audit log row written.

                    **Role gate**: `ADMIN`.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200",
                    description = "User reactivated.",
                    content = @Content(schema = @Schema(implementation = UserAdminView.class))),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthenticated"),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound"),
            @ApiResponse(responseCode = "409", ref = "#/components/responses/Conflict")
    })
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/{id}/reactivate")
    public UserAdminView reactivate(
            @AuthenticationPrincipal JwtPrincipal principal,
            @Parameter(description = "User ID to reactivate.", example = "23")
            @PathVariable Long id) {
        return adminUserService.reactivate(principal.userId(), id);
    }
}
