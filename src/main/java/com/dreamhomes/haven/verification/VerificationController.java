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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import com.dreamhomes.haven.verification.automation.AutomatedCheckResultResponse;
import com.dreamhomes.haven.verification.automation.VerificationAutomationResultRepository;
import com.dreamhomes.haven.verification.dto.UploadedDocumentResponse;
import com.dreamhomes.haven.verification.liveness.LivenessCheckResult;
import com.dreamhomes.haven.verification.liveness.LivenessCheckResultResponse;
import com.dreamhomes.haven.verification.liveness.LivenessCheckService;
import com.dreamhomes.haven.verification.storage.VerificationDocumentStorage;

import java.util.List;

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
    private final VerificationDocumentStorage verificationDocumentStorage;
    private final LivenessCheckService livenessCheckService;
    private final VerificationAutomationResultRepository automationResultRepository;

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
                new SubmitVerificationCommand(request.type(), request.propertyId(),
                        request.documentRefs(), request.livenessCheckId()));
        return toResponse(saved);
    }

    @Operation(
            summary = "List my verification submissions",
            description = """
                    Returns the caller's own verification submissions, newest first. Use this to
                    check the status of a submission you made — PENDING / APPROVED / REJECTED.
                    The persona audit found every persona who submits a verification had no way
                    to see their own submissions; this is that read-side.

                    Scoped strictly to the caller — there is no `?userId=` parameter; the admin
                    queue (`GET /api/admin/verifications`) is the cross-user view.

                    **REJECTED rows carry `decisionReason`** — the reason the admin supplied
                    when rejecting (Item 21, post-session-tasks.md). The UI should render this
                    prominently so the user knows what to fix on resubmit. PENDING and APPROVED
                    rows always return `decisionReason: null` regardless of any stored value.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Paginated list of the caller's submissions."),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthenticated")
    })
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/mine")
    public Page<VerificationResponse> listMine(@AuthenticationPrincipal JwtPrincipal principal,
                                               @PageableDefault(size = 20) Pageable pageable) {
        return verificationService.listMine(principal.userId(), pageable).map(this::toResponse);
    }

    @Operation(
            summary = "Upload a verification document",
            description = """
                    Uploads a single file (NIN slip, C of O, agent licence, etc.) into
                    the platform's R2 bucket under {@code verifications/{userId}/}.
                    Returns the URL the file is hostable at; the client should paste
                    that URL into the subsequent `POST /api/verifications` call inside
                    `documentRefs`.

                    Persona audit: every persona who submits a verification flagged
                    the absence of a real upload endpoint. They were being asked to
                    host their own NIN / C of O on a public CDN — a data-leakage
                    scenario the verification flow itself was supposed to prevent.

                    `multipart/form-data` request with field name `file`.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "File uploaded. Response contains the public URL.",
                    content = @Content(schema = @Schema(implementation = UploadedDocumentResponse.class),
                            examples = @ExampleObject(name = "UploadedNin", value = """
                                    { "url": "https://pub-abc.r2.dev/verifications/42/c3a8.jpg" }
                                    """))),
            @ApiResponse(responseCode = "400", ref = "#/components/responses/ValidationFailed"),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthenticated")
    })
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping(value = "/files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public UploadedDocumentResponse uploadFile(
            @AuthenticationPrincipal JwtPrincipal principal,
            @RequestParam("file") MultipartFile file) {
        String url = verificationDocumentStorage.upload(file, principal.userId());
        return new UploadedDocumentResponse(url);
    }

    @Operation(
            summary = "(MOCKED) Run a liveness check before verification submission",
            description = """
                    **⚠️ This endpoint is MOCKED for v1.** It always returns a PASSED result
                    with score 0.97 regardless of input. The integration point is in place
                    so v2 can swap in a real biometric provider (Smile ID, Dojah, Sourcefin)
                    without changing the caller contract.

                    **What v2 will do:** open a camera session, ask the user to blink /
                    turn head / smile on command, verify the motion is real-time (not a
                    recording), and return PASSED/FAILED with a confidence score.

                    **What v1 does:** returns PASSED. The `_mocked: true` flag in
                    the response makes it obvious this isn't real.

                    Callers should still consume + persist the response; verification
                    submit accepts the returned `id` as `livenessCheckId` on the request
                    body, and the same id cannot be replayed across multiple submissions
                    (server stamps `consumed_at` on the row).
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201",
                    description = "(MOCKED) Liveness check passed — caller forwards `id` to verification submit.",
                    content = @Content(
                            schema = @Schema(implementation = LivenessCheckResultResponse.class),
                            examples = @ExampleObject(name = "MockedPassed", value = """
                                    { "id": 42, "status": "PASSED", "score": 0.97,
                                      "provider": "MOCK", "checkedAt": "2026-05-24T08:30:00Z",
                                      "_mocked": true }
                                    """))),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthenticated")
    })
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/liveness-check")
    @ResponseStatus(HttpStatus.CREATED)
    public LivenessCheckResultResponse runLivenessCheck(
            @AuthenticationPrincipal JwtPrincipal principal) {
        LivenessCheckResult row = livenessCheckService.runMockedCheck(principal.userId());
        return new LivenessCheckResultResponse(row.getId(), row.getStatus(), row.getScore(),
                row.getProviderName(), row.getCreatedAt(),
                "MOCK".equals(row.getProviderName()));
    }

    private VerificationResponse toResponse(Verification v) {
        // decisionReason is only meaningful on REJECTED rows — see Item 21
        // (docs/demo-prep/post-session-tasks.md). The submitter sees the admin's reason
        // and knows what to fix on resubmit; PENDING / APPROVED rows leak no value.
        String reason = v.getStatus() == com.dreamhomes.haven.verification.model.VerificationStatus.REJECTED
                ? v.getDecisionReason() : null;
        // Item 20: surface the automated check rows so Vista (and admins via this same
        // response shape) can show what the provider extracted. Null when nothing ran.
        List<AutomatedCheckResultResponse> automatedChecks = automationResultRepository
                .findByVerificationIdOrderByRunAtAsc(v.getId()).stream()
                .map(AutomatedCheckResultResponse::from)
                .toList();
        return new VerificationResponse(v.getId(), v.getType(), v.getStatus(),
                v.getSubmitterUserId(), v.getTargetUserId(), v.getTargetPropertyId(),
                v.getDocumentRefs(), v.getSubmittedAt(), v.getDecidedAt(), reason,
                automatedChecks.isEmpty() ? null : automatedChecks);
    }
}
