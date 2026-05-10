package com.dreamhomes.haven.admin.controller;

import com.dreamhomes.haven.admin.dto.RejectVerificationRequest;
import com.dreamhomes.haven.admin.service.AdminVerificationService;
import com.dreamhomes.haven.auth.JwtPrincipal;
import com.dreamhomes.haven.verification.dto.VerificationAdminView;
import com.dreamhomes.haven.verification.model.VerificationType;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin queue + decision endpoints. Whole controller is gated by {@code @PreAuthorize}
 * at the class level — non-admins get 403 before any handler runs.
 */
@RestController
@RequestMapping("/api/admin/verifications")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin")
public class AdminVerificationController {

    private static final int MAX_PAGE_SIZE = 100;

    private final AdminVerificationService adminVerificationService;

    @Operation(
            summary = "List pending verifications by type",
            description = """
                    Paginated queue of `PENDING` verifications filtered by type. Required \
                    `?type=` query parameter — Dayo works one track at a time (do all the \
                    OWNER_IDENTITY submissions first, then APPLICANT_IDENTITY, etc.).

                    Returns the admin-side view (`VerificationAdminView`) with submitter \
                    context and document refs needed to make the decision.

                    **Role gate**: `ADMIN`.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200",
                    description = "Paginated PENDING verifications of the requested type.",
                    content = @Content(
                            examples = @ExampleObject(name = "PendingOwnerIdentityQueue", value = """
                                    { "content": [
                                        { "id": 99, "type": "OWNER_IDENTITY", "status": "PENDING",
                                          "submitterUserId": 7, "targetUserId": 7, "targetPropertyId": null,
                                          "documentRefs": { "kind": "C_OF_O", "ref": "lagos/lekki/2024/00123" },
                                          "submittedAt": "2026-05-10T08:30:00Z",
                                          "decidedAt": null, "decisionReason": null }
                                      ],
                                      "page": { "size": 20, "number": 0, "totalElements": 1, "totalPages": 1 } }
                                    """))),
            @ApiResponse(responseCode = "400", ref = "#/components/responses/ValidationFailed"),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthenticated"),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden")
    })
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping
    public Page<VerificationAdminView> listPending(
            @Parameter(description = "Verification type to filter by.", example = "OWNER_IDENTITY", required = true)
            @RequestParam VerificationType type,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0),
                Math.min(Math.max(size, 1), MAX_PAGE_SIZE));
        return adminVerificationService.listPending(type, pageable);
    }

    @Operation(
            summary = "Approve a verification submission",
            description = """
                    Transitions a `PENDING` verification to `APPROVED`. Per-type badge stamp \
                    side effects:

                    - `OWNER_IDENTITY` / `APPLICANT_IDENTITY` → stamps \
                      `users.identity_verified_at` on the submitter.
                    - `AGENT_CREDENTIALS` → stamps \
                      `agent_profiles.credential_verified_at`.
                    - `PROPERTY_DOCUMENTS` → stamps \
                      `properties.documents_verified_at` on the target property.

                    Submitter receives a `VERIFICATION_APPROVED` notification. Audit log \
                    row written.

                    Cannot approve a row that's already decided (409).

                    **Role gate**: `ADMIN`.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200",
                    description = "Verification approved; per-type badge stamped.",
                    content = @Content(schema = @Schema(implementation = VerificationAdminView.class))),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthenticated"),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound"),
            @ApiResponse(responseCode = "409", ref = "#/components/responses/Conflict")
    })
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/{id}/approve")
    public VerificationAdminView approve(
            @AuthenticationPrincipal JwtPrincipal principal,
            @Parameter(description = "Verification ID.", example = "99")
            @PathVariable Long id) {
        return adminVerificationService.approve(principal.userId(), id, null);
    }

    @Operation(
            summary = "Reject a verification submission with a reason",
            description = """
                    Transitions a `PENDING` verification to `REJECTED`. The reason is \
                    surfaced in the submitter's notification and visible on their own \
                    `GET /verifications/mine` (forthcoming) so they know exactly what to fix.

                    **Reason**: required, non-empty (validated). Empty / whitespace-only \
                    reason is rejected with 400 BEFORE any DB write — no wasted round-trip \
                    to Postgres on a misuse path.

                    Audit log row written.

                    **Role gate**: `ADMIN`.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200",
                    description = "Verification rejected; reason stored.",
                    content = @Content(
                            schema = @Schema(implementation = VerificationAdminView.class),
                            examples = @ExampleObject(name = "RejectedWithReason", value = """
                                    { "id": 99, "type": "OWNER_IDENTITY", "status": "REJECTED",
                                      "submitterUserId": 7, "targetUserId": 7, "targetPropertyId": null,
                                      "documentRefs": { "kind": "C_OF_O", "ref": "lagos/lekki/2024/00123" },
                                      "submittedAt": "2026-05-10T08:30:00Z",
                                      "decidedAt":   "2026-05-10T11:00:00Z",
                                      "decisionReason": "C of O address does not match the listing address." }
                                    """))),
            @ApiResponse(responseCode = "400", ref = "#/components/responses/ValidationFailed"),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthenticated"),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound"),
            @ApiResponse(responseCode = "409", ref = "#/components/responses/Conflict")
    })
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/{id}/reject")
    public VerificationAdminView reject(
            @AuthenticationPrincipal JwtPrincipal principal,
            @Parameter(description = "Verification ID.", example = "99")
            @PathVariable Long id,
            @Valid @RequestBody RejectVerificationRequest request) {
        return adminVerificationService.reject(principal.userId(), id, request.reason());
    }
}
