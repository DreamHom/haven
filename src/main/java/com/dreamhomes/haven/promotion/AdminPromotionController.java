package com.dreamhomes.haven.promotion;
import com.dreamhomes.haven.auth.JwtPrincipal;
import com.dreamhomes.haven.promotion.dto.ApprovePromotionRequest;
import com.dreamhomes.haven.promotion.dto.PromotionActionRequest;
import com.dreamhomes.haven.promotion.dto.PromotionMetricsResponse;
import com.dreamhomes.haven.promotion.dto.PromotionMetricsSummaryResponse;
import com.dreamhomes.haven.promotion.dto.PromotionResponse;
import com.dreamhomes.haven.promotion.model.PromotionPlacement;
import com.dreamhomes.haven.promotion.model.PromotionStatus;
import com.dreamhomes.haven.promotion.model.PromotionTargetType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping("/api/admin/promotions")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@io.swagger.v3.oas.annotations.tags.Tag(name = "Admin")
public class AdminPromotionController {

    private final PromotionService promotionService;

    @GetMapping
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            summary = "Filtered admin moderation queue for promotions",
            description = """
                    **Persona**: Dayo (S2) — the admin moderation seat. This is the queue Dayo
                    works from every morning: pending-approval promotions, recently-active ones
                    to spot-check, anything REJECTED / REVOKED for audit follow-up.

                    All four query params are optional. Combine them however the use case demands
                    (e.g. `?status=PENDING` to see the action queue; `?placement=HOMEPAGE_FEATURED`
                    to see what's live on the homepage right now; `?createdByUserId=42` to audit
                    a specific sponsor's history).

                    Default sort: newest first, paginated (default size 20).
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Paginated promotion list matching the filters (may be empty)."),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthenticated"),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden")
    })
    public Page<PromotionResponse> search(@RequestParam(required = false) PromotionStatus status,
                                          @RequestParam(required = false) PromotionTargetType targetType,
                                          @RequestParam(required = false) PromotionPlacement placement,
                                          @RequestParam(required = false) Long createdByUserId,
                                          @PageableDefault(size = 20) Pageable pageable) {
        return promotionService.adminSearch(status, targetType, placement, createdByUserId, pageable);
    }

    @GetMapping("/{id}")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            summary = "Read any promotion (admin view)",
            description = """
                    **Persona**: Dayo drilling into a specific row from the moderation queue.

                    Same payload shape as the sponsor-facing `GET /api/promotions/{id}`, but admins
                    can read ANY promotion regardless of who created it.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Promotion detail."),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthenticated"),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound")
    })
    public PromotionResponse find(@AuthenticationPrincipal JwtPrincipal principal,
                                  @PathVariable Long id) {
        return promotionService.findMineOrAdmin(principal.userId(), principal.role(), id);
    }

    @PostMapping("/{id}/approve")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            summary = "Approve a PENDING promotion (transitions to ACTIVE)",
            description = """
                    **Persona**: Dayo — flips a PENDING promotion to ACTIVE. As soon as this commits,
                    the promotion becomes visible on the matching public placement endpoint.

                    Body is optional. When supplied:
                    - `priority` — sort-priority within the placement (higher = surfaces first).
                    - `reason` — admin's note on the approval (for audit).

                    Transition guard: source status MUST be PENDING. Re-approving an already-ACTIVE
                    or terminal (REJECTED/REVOKED/EXPIRED) row returns 409.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Promotion is now ACTIVE."),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthenticated"),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound"),
            @ApiResponse(responseCode = "409", ref = "#/components/responses/Conflict")
    })
    public PromotionResponse approve(@AuthenticationPrincipal JwtPrincipal principal,
                                     @PathVariable Long id,
                                     @RequestBody(required = false) ApprovePromotionRequest request) {
        Integer priority = request == null ? null : request.priority();
        String reason = request == null ? null : request.reason();
        return promotionService.approve(principal.userId(), id, priority, reason);
    }

    @PostMapping("/{id}/reject")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            summary = "Reject a PENDING promotion (terminal — sponsor can submit fresh)",
            description = """
                    **Persona**: Dayo — denies a PENDING promotion. The sponsor sees the row flip
                    to REJECTED with the reason on `GET /api/promotions/mine` and on the detail
                    view. They can submit a fresh promotion to retry; the rejected row stays for
                    audit history.

                    `reason` is required (validated by the body DTO) and surfaced to the sponsor
                    verbatim — keep the language constructive ("photo doesn't meet our minimum
                    resolution" beats "rejected").
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Promotion is now REJECTED."),
            @ApiResponse(responseCode = "400", ref = "#/components/responses/ValidationFailed"),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthenticated"),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound"),
            @ApiResponse(responseCode = "409", ref = "#/components/responses/Conflict")
    })
    public PromotionResponse reject(@AuthenticationPrincipal JwtPrincipal principal,
                                    @PathVariable Long id,
                                    @Valid @RequestBody PromotionActionRequest request) {
        return promotionService.reject(principal.userId(), id, request.reason());
    }

    @PostMapping("/{id}/pause")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            summary = "Pause an ACTIVE promotion (reversible via /resume)",
            description = """
                    **Persona**: Dayo — temporarily takes an ACTIVE promotion off the public
                    placements without permanently revoking it. Use when the sponsor needs to
                    fix something (refreshed photos, corrected listing detail) but the run
                    isn't actually over.

                    Transition guard: source status MUST be ACTIVE. `reason` required.

                    Compare with `/revoke` which is terminal — `/pause` is reversible.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Promotion is now PAUSED."),
            @ApiResponse(responseCode = "400", ref = "#/components/responses/ValidationFailed"),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthenticated"),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound"),
            @ApiResponse(responseCode = "409", ref = "#/components/responses/Conflict")
    })
    public PromotionResponse pause(@AuthenticationPrincipal JwtPrincipal principal,
                                   @PathVariable Long id,
                                   @Valid @RequestBody PromotionActionRequest request) {
        return promotionService.pause(principal.userId(), id, request.reason());
    }

    @PostMapping("/{id}/resume")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            summary = "Resume a PAUSED promotion (transitions back to ACTIVE)",
            description = """
                    **Persona**: Dayo — flips a PAUSED promotion back to ACTIVE so it resumes
                    surfacing on the public placement. Body is optional; if supplied, `reason`
                    is recorded as a note on the resume action.

                    Transition guard: source status MUST be PAUSED.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Promotion is now ACTIVE again."),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthenticated"),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound"),
            @ApiResponse(responseCode = "409", ref = "#/components/responses/Conflict")
    })
    public PromotionResponse resume(@AuthenticationPrincipal JwtPrincipal principal,
                                    @PathVariable Long id,
                                    @RequestBody(required = false) PromotionActionRequest request) {
        return promotionService.resume(principal.userId(), id, request == null ? null : request.reason());
    }

    @PostMapping("/{id}/revoke")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            summary = "Revoke a promotion (terminal — kills the run permanently)",
            description = """
                    **Persona**: Dayo — pulls an ACTIVE or PAUSED promotion off the placements
                    permanently. Use when the underlying listing or agent has been suspended /
                    taken-down, or when the promotion content violated policy after going live.

                    Transition guard: source status MUST be ACTIVE or PAUSED. `reason` required —
                    surfaced to the sponsor on their detail view so they understand why their spend
                    was cut short.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Promotion is now REVOKED (terminal)."),
            @ApiResponse(responseCode = "400", ref = "#/components/responses/ValidationFailed"),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthenticated"),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound"),
            @ApiResponse(responseCode = "409", ref = "#/components/responses/Conflict")
    })
    public PromotionResponse revoke(@AuthenticationPrincipal JwtPrincipal principal,
                                    @PathVariable Long id,
                                    @Valid @RequestBody PromotionActionRequest request) {
        return promotionService.revoke(principal.userId(), id, request.reason());
    }

    @GetMapping("/{id}/metrics")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            summary = "Per-promotion metrics (admin view — same shape as sponsor's)",
            description = """
                    **Persona**: Dayo drilling into a specific promotion's performance during
                    moderation review.

                    Same shape as `GET /api/promotions/{id}/metrics` — impressions, clicks, CTR.
                    Admin scope means admins can fetch metrics for any promotion regardless of
                    sponsor.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Per-promotion metrics aggregate."),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthenticated"),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound")
    })
    public PromotionMetricsResponse metrics(@AuthenticationPrincipal JwtPrincipal principal,
                                            @PathVariable Long id) {
        return promotionService.metricsMineOrAdmin(principal.userId(), principal.role(), id);
    }

    @GetMapping("/metrics/summary")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            summary = "Cross-promotion summary metrics for the admin dashboard",
            description = """
                    **Persona**: Dayo opening the promotions dashboard — wants the platform-wide
                    picture: total active campaigns, total impressions, total clicks, CTR by
                    placement, breakdown by status.

                    No filters — always returns the platform-wide summary.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Cross-promotion summary."),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthenticated"),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden")
    })
    public PromotionMetricsSummaryResponse metricsSummary() {
        return promotionService.adminMetricsSummary();
    }
}
