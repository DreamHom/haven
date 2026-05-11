package com.dreamhomes.haven.verification;

import com.dreamhomes.haven.auth.JwtPrincipal;
import com.dreamhomes.haven.verification.dto.SubmitVerificationCommand;
import com.dreamhomes.haven.verification.dto.SubmitVerificationRequest;
import com.dreamhomes.haven.verification.dto.VerificationResponse;
import com.dreamhomes.haven.verification.model.Verification;
import com.dreamhomes.haven.verification.service.VerificationService;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Submission endpoint for the four verification tracks. Admin queue + decision endpoints
 * live under {@code /api/admin/...} — see the admin package.
 */
@RestController
@RequestMapping("/api/verifications")
@RequiredArgsConstructor
@Tag(name = "Verifications")
public class VerificationController {

    private final VerificationService verificationService;

    @Operation(
            summary = "Submit a verification for admin review",
            description = """
                    Records a `Verification` row in `PENDING` for an admin to review. Four tracks:

                    - `OWNER_IDENTITY` — only OWNER role can submit (NIN / C of O proving identity).
                    - `APPLICANT_IDENTITY` — only APPLICANT role can submit (NIN).
                    - `AGENT_CREDENTIALS` — only AGENT role can submit (real-estate licence).
                    - `PROPERTY_DOCUMENTS` — OWNER role; references a `propertyId` they own.

                    On admin approval the corresponding badge is stamped:
                    - identity → `users.identity_verified_at`
                    - agent credential → `agent_profiles.credential_verified_at`
                    - property documents → `properties.documents_verified_at`

                    **Constraints**:
                    - At most one PENDING submission of the same type per user — duplicates → 409.
                    - Role must match the type (e.g. APPLICANT cannot submit OWNER_IDENTITY) → 403.
                    - For PROPERTY_DOCUMENTS, the target property must belong to the caller.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201",
                    description = "Verification queued for admin review.",
                    content = @Content(
                            schema = @Schema(implementation = VerificationResponse.class),
                            examples = @ExampleObject(name = "OwnerIdentitySubmission", value = """
                                    { "id": 99, "type": "OWNER_IDENTITY", "status": "PENDING",
                                      "submitterUserId": 7, "targetUserId": 7, "targetPropertyId": null,
                                      "documentRefs": { "kind": "C_OF_O", "ref": "lagos/lekki/2024/00123" },
                                      "submittedAt": "2026-05-10T08:30:00Z",
                                      "decidedAt": null }
                                    """))),
            @ApiResponse(responseCode = "400", ref = "#/components/responses/ValidationFailed"),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthenticated"),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound"),
            @ApiResponse(responseCode = "409", ref = "#/components/responses/Conflict")
    })
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('OWNER', 'AGENT', 'APPLICANT')")
    public VerificationResponse submit(@AuthenticationPrincipal JwtPrincipal principal,
                                       @Valid @RequestBody SubmitVerificationRequest request) {
        Verification saved = verificationService.submit(principal.userId(),
                new SubmitVerificationCommand(request.type(), request.propertyId(), request.documentRefs()));
        return new VerificationResponse(saved.getId(), saved.getType(), saved.getStatus(),
                saved.getSubmitterUserId(), saved.getTargetUserId(), saved.getTargetPropertyId(),
                saved.getDocumentRefs(), saved.getSubmittedAt(), saved.getDecidedAt());
    }
}
