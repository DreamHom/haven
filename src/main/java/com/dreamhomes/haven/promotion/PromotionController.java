package com.dreamhomes.haven.promotion;
import com.dreamhomes.haven.auth.JwtPrincipal;
import com.dreamhomes.haven.promotion.dto.CreatePromotionRequest;
import com.dreamhomes.haven.promotion.dto.PromotionMetricsResponse;
import com.dreamhomes.haven.promotion.dto.PromotionPublicResponse;
import com.dreamhomes.haven.promotion.dto.PromotionResponse;
import com.dreamhomes.haven.promotion.dto.PromotionTrackRequest;
import com.dreamhomes.haven.promotion.model.PromotionPlacement;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping("/api/promotions")
@RequiredArgsConstructor
@Tag(name = "Promotions")
public class PromotionController {

    private final PromotionService promotionService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            summary = "Submit a new promotion request",
            description = """
                    **Persona**: Amaka (S5), Biodun (S5), Emeka (S5) — owners and agents who want
                    paid surfacing for one of their listings, or (for agents) for their agent profile.

                    Creates a new promotion in `PENDING` state for the target placement and date range
                    in the body. The promotion is invisible to the public until an admin reviews and
                    flips it to `ACTIVE` via `POST /api/admin/promotions/{id}/approve`. Sponsor sees
                    it immediately on `GET /api/promotions/mine` with status badging.

                    **Preconditions**
                    - Caller is an authenticated `OWNER` or `AGENT`
                    - For listing-targeted placements (`HOMEPAGE_FEATURED`, `LISTING_SEARCH_TOP`),
                      caller must own (or be the active assigned agent on) the target listing
                    - For agent-targeted placement (`AGENT_DIRECTORY_TOP`), caller must be an `AGENT`
                      targeting their own profile
                    - End-date must be after start-date; window must be in the future
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Promotion created in PENDING state."),
            @ApiResponse(responseCode = "400", ref = "#/components/responses/ValidationFailed"),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthenticated"),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound"),
            @ApiResponse(responseCode = "409", ref = "#/components/responses/Conflict")
    })
    public PromotionResponse request(@AuthenticationPrincipal JwtPrincipal principal,
                                     @Valid @RequestBody CreatePromotionRequest request) {
        return promotionService.request(principal.userId(), request);
    }

    @GetMapping("/mine")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            summary = "List your own promotions (paginated, newest first)",
            description = """
                    **Persona**: Amaka, Biodun, Emeka — sponsors checking on their own pipeline.

                    Returns every promotion the calling sponsor has ever submitted, regardless of
                    status (PENDING / ACTIVE / PAUSED / REJECTED / REVOKED / EXPIRED). Sponsors use
                    this to track approval status, see metrics, and decide whether to retry after
                    a rejection.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Paginated promotion list, newest first."),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthenticated")
    })
    public Page<PromotionResponse> mine(@AuthenticationPrincipal JwtPrincipal principal,
                                        @PageableDefault(size = 20) Pageable pageable) {
        return promotionService.listMine(principal.userId(), pageable);
    }

    @GetMapping("/{id}")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            summary = "Read one of your promotions (or any, if admin)",
            description = """
                    **Persona**: sponsor reading their own promotion detail; admin reading any
                    promotion during moderation review.

                    Returns the full promotion including status, target, placement, window, decision
                    metadata (`decidedAt`, `decidedByAdminId`, `decisionReason`), and the embedded
                    target title/headline. Sponsors only see their own rows; admins see any row.
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

    @GetMapping("/{id}/metrics")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            summary = "Read impression + click metrics for one of your promotions",
            description = """
                    **Persona**: sponsor wanting to see if their spend is doing anything; admin
                    drilling into a specific promotion's performance.

                    Returns aggregate counts (impressions, clicks, CTR) for the lifetime of the
                    promotion. Counts come from the `POST /{id}/impression` and `POST /{id}/click`
                    tracking endpoints fired by Vista when promotion cards render and are tapped.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Metrics aggregate."),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthenticated"),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound")
    })
    public PromotionMetricsResponse metrics(@AuthenticationPrincipal JwtPrincipal principal,
                                            @PathVariable Long id) {
        return promotionService.metricsMineOrAdmin(principal.userId(), principal.role(), id);
    }

    @SecurityRequirements
    @GetMapping("/homepage-featured")
    @Operation(
            summary = "Read currently-ACTIVE promotions for the homepage Featured placement",
            description = """
                    **Persona**: Temi, Ngozi browsing — Vista calls this on every homepage render
                    to populate the Featured slot.

                    Returns only promotions with status `ACTIVE` AND placement `HOMEPAGE_FEATURED`
                    AND within their start/end window. Public — no auth. Vista MUST fire
                    `POST /{id}/impression` once per visible render and `POST /{id}/click` on tap.
                    Display the "Featured" label on every rendered card.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Paginated active promotions (may be empty).")
    })
    public Page<PromotionPublicResponse> homepageFeatured(@PageableDefault(size = 10) Pageable pageable) {
        return promotionService.publicFor(PromotionPlacement.HOMEPAGE_FEATURED, pageable);
    }

    @SecurityRequirements
    @GetMapping("/listing-search-top")
    @Operation(
            summary = "Read currently-ACTIVE promotions for the search-results top slot",
            description = """
                    **Persona**: Temi, Ngozi searching for listings — Vista calls this on every
                    search-results page render.

                    Returns only promotions with status `ACTIVE` AND placement `LISTING_SEARCH_TOP`
                    AND within their start/end window. Public — no auth. Vista MUST display the
                    "Sponsored" label prominently on rendered cards (stronger disclosure expectation
                    than `Featured` since search results are the primary discovery surface). Fire
                    impression + click tracking on render and tap.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Paginated active promotions (may be empty).")
    })
    public Page<PromotionPublicResponse> listingSearchTop(@PageableDefault(size = 10) Pageable pageable) {
        return promotionService.publicFor(PromotionPlacement.LISTING_SEARCH_TOP, pageable);
    }

    @SecurityRequirements
    @GetMapping("/agent-directory-top")
    @Operation(
            summary = "Read currently-ACTIVE promotions for the agent-directory top slot",
            description = """
                    **Persona**: Temi, Ngozi browsing the agent directory — Vista calls this on
                    every directory render.

                    Returns only promotions with status `ACTIVE` AND placement `AGENT_DIRECTORY_TOP`
                    AND within their start/end window. Public — no auth. Display "Featured" label.
                    Fire impression + click tracking.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Paginated active promotions (may be empty).")
    })
    public Page<PromotionPublicResponse> agentDirectoryTop(@PageableDefault(size = 10) Pageable pageable) {
        return promotionService.publicFor(PromotionPlacement.AGENT_DIRECTORY_TOP, pageable);
    }

    @SecurityRequirements
    @PostMapping("/{id}/impression")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
            summary = "Record a promotion impression",
            description = """
                    Vista fires this ONCE per visible render of a promotion card on the public
                    placement endpoints above (debounce — don't fire on every scroll). Anonymous
                    OK — the body declares which placement the impression came from so the metric
                    is segmented correctly when the same promotion is shown across multiple
                    placements.

                    Sponsor + admin metrics endpoints aggregate these counts into the
                    `impressions` and `clickThroughRate` fields on `PromotionMetricsResponse`.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Impression recorded."),
            @ApiResponse(responseCode = "400", ref = "#/components/responses/ValidationFailed"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound")
    })
    public void impression(@AuthenticationPrincipal JwtPrincipal principal,
                           @PathVariable Long id,
                           @Valid @RequestBody PromotionTrackRequest request) {
        promotionService.recordImpression(id, request.placement(), principal == null ? null : principal.userId());
    }

    @SecurityRequirements
    @PostMapping("/{id}/click")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
            summary = "Record a promotion click",
            description = """
                    Vista fires this when a user taps / clicks a rendered promotion card. Anonymous
                    OK — the body declares which placement the click came from. Combined with
                    impression tracking this drives the click-through-rate metric the sponsor and
                    admin see on the metrics endpoints.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Click recorded."),
            @ApiResponse(responseCode = "400", ref = "#/components/responses/ValidationFailed"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound")
    })
    public void click(@AuthenticationPrincipal JwtPrincipal principal,
                      @PathVariable Long id,
                      @Valid @RequestBody PromotionTrackRequest request) {
        promotionService.recordClick(id, request.placement(), principal == null ? null : principal.userId());
    }
}
