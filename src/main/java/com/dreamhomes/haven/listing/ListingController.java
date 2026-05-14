package com.dreamhomes.haven.listing;

import com.dreamhomes.haven.auth.JwtPrincipal;
import com.dreamhomes.haven.listing.dto.CreateListingRequest;
import com.dreamhomes.haven.listing.dto.ListingResponse;
import com.dreamhomes.haven.listing.dto.ListingWithProperty;
import com.dreamhomes.haven.listing.dto.UpdateListingRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/listings")
@org.springframework.validation.annotation.Validated
@RequiredArgsConstructor
@Tag(name = "Listings")
public class ListingController {

    private final ListingService listingService;
    private final ListingMapper listingMapper;

    @Operation(
            summary = "Publish a listing",
            description = """
                    Creates a `Listing` against one of the caller's properties at the chosen \
                    type (`RENT` / `SALE`) and price. The listing starts in `LIVE` status — \
                    publicly browsable immediately, no admin pre-approval gate. Admin only \
                    intervenes reactively via takedown.

                    **Ownership**: the caller must own the target `Property` (FK check). \
                    Trying to list someone else's property returns 403.

                    **Role gate**: `OWNER` only. Agents publish via the agent-assignment flow.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201",
                    description = "Listing created in LIVE.",
                    content = @Content(
                            schema = @Schema(implementation = ListingResponse.class),
                            examples = @ExampleObject(name = "OpenRental", value = """
                                    { "id": 17, "propertyId": 42, "ownerId": 7,
                                      "type": "RENT", "price": 850000, "currency": "NGN",
                                      "status": "LIVE", "title": "3-bed apartment, Lekki",
                                      "description": "Top-floor with sea view.",
                                      "createdAt": "2026-05-10T08:30:00Z" }
                                    """))),
            @ApiResponse(responseCode = "400", ref = "#/components/responses/ValidationFailed"),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthenticated"),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound")
    })
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('OWNER')")
    public ListingResponse create(@AuthenticationPrincipal JwtPrincipal principal,
                                  @Valid @RequestBody CreateListingRequest request) {
        return listingMapper.toResponse(
                listingService.create(principal.userId(), request.toCommand()), null);
    }

    @Operation(
            summary = "Publish many listings in one call",
            description = """
                    Bulk variant of {@code POST /api/listings}. Persona audit (Biodun): a
                    developer publishing the same tower across 60 units shouldn't fan out
                    60 individual calls and reconcile partial state. One transaction — if
                    any single listing fails validation or ownership check the whole batch
                    rolls back. Responses come back in the same order.

                    **Limits**: at most 100 listings per call.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "All listings created."),
            @ApiResponse(responseCode = "400", ref = "#/components/responses/ValidationFailed"),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthenticated"),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound")
    })
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/bulk")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('OWNER')")
    public java.util.List<ListingResponse> createBulk(
            @AuthenticationPrincipal JwtPrincipal principal,
            @Valid @RequestBody @jakarta.validation.constraints.Size(min = 1, max = 100)
            java.util.List<CreateListingRequest> requests) {
        java.util.List<ListingResponse> out = new java.util.ArrayList<>(requests.size());
        for (CreateListingRequest r : requests) {
            out.add(listingMapper.toResponse(
                    listingService.create(principal.userId(), r.toCommand()), null));
        }
        return out;
    }

    @Operation(
            summary = "Browse public listings",
            description = """
                    Paginated list of publicly-visible listings (status `LIVE`, not \
                    administratively taken down). Public — no auth required, designed for \
                    anonymous discovery.

                    Response carries `Cache-Control: public, max-age=...` so a CDN or \
                    browser can serve repeat hits without round-tripping to Postgres. \
                    Newly published or status-changed listings can take one cache TTL to \
                    reflect.

                    Use `?page=` and `?size=` for pagination (defaults: page 0, size 20). \
                    Sort defaults to creation time descending.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200",
                    description = "Paginated `Page<ListingResponse>` envelope.",
                    content = @Content(
                            examples = @ExampleObject(name = "FirstPage", value = """
                                    { "content": [
                                        { "id": 17, "propertyId": 42, "ownerId": 7,
                                          "type": "RENT", "price": 850000, "currency": "NGN",
                                          "status": "LIVE", "title": "3-bed apartment, Lekki",
                                          "createdAt": "2026-05-10T08:30:00Z" }
                                      ],
                                      "page": { "size": 20, "number": 0, "totalElements": 1, "totalPages": 1 } }
                                    """)))
    })
    @SecurityRequirements // public
    @GetMapping
    public Page<ListingResponse> browse(
            @Parameter(description = "Filter by listing type: RENT or SALE.")
            @org.springframework.web.bind.annotation.RequestParam(required = false)
            com.dreamhomes.haven.listing.model.ListingType listingType,
            @Parameter(description = "Minimum asking price (inclusive).")
            @org.springframework.web.bind.annotation.RequestParam(required = false)
            java.math.BigDecimal priceMin,
            @Parameter(description = "Maximum asking price (inclusive).")
            @org.springframework.web.bind.annotation.RequestParam(required = false)
            java.math.BigDecimal priceMax,
            @Parameter(description = "Restrict to listings whose property has exactly this bedroom count.")
            @org.springframework.web.bind.annotation.RequestParam(required = false)
            Integer bedrooms,
            @Parameter(description = "Filter by property type: APARTMENT, HOUSE, SELF_CONTAIN, etc.")
            @org.springframework.web.bind.annotation.RequestParam(required = false)
            com.dreamhomes.haven.property.model.PropertyType propertyType,
            @Parameter(description = "Case-insensitive substring of the property address (e.g. 'Yaba').")
            @org.springframework.web.bind.annotation.RequestParam(required = false)
            String location,
            @Parameter(description = "Standard Spring pagination — defaults to page=0&size=20.")
            @PageableDefault(size = 20) Pageable pageable) {
        return listingService.browsePublic(listingType, priceMin, priceMax, bedrooms,
                        propertyType, location, pageable)
                .map(lwp -> listingMapper.toResponse(lwp.listing(), lwp.property()));
    }

    @Operation(
            summary = "List my listings",
            description = """
                    Returns the caller's own listings across all statuses (LIVE, PAUSED, CLOSED, \
                    TAKEN_DOWN), newest first. Scoped strictly to the authenticated owner — \
                    there is no `?ownerId=` parameter. The persona audit (Amaka, Biodun) \
                    flagged this as a gap: an owner with even 3-4 listings couldn't see what \
                    they own without remembering the IDs.

                    **Role gate**: `OWNER` only. Agents see their assignments via \
                    `GET /api/agent-listings/mine`.

                    **Lead-management rollup** (Biodun's "dashboard" ask) is **not** in this \
                    payload. Each `ListingResponse` carries `viewCount` (eagerly aggregated, \
                    free), but offer count, pending-inspection count, and saves count are not \
                    embedded. The frontend has to fan out:
                    - `GET /api/offers/mine` then group by `listingId` for offer counts (the \
                      response includes offers where the caller is *either* applicant or \
                      listing owner — owners get all offers across their listings in one call).
                    - `GET /api/listings/{id}/slots` per listing for upcoming inspections.
                    A single `?include=engagement` rollup is on the roadmap; it is not built \
                    yet, so do not assume any extra fields will appear here.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Paginated list of the caller's listings."),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthenticated"),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden")
    })
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/mine")
    @PreAuthorize("hasRole('OWNER')")
    public Page<ListingResponse> listMine(@AuthenticationPrincipal JwtPrincipal principal,
                                          @PageableDefault(size = 20) Pageable pageable) {
        return listingService.listMine(principal.userId(), pageable)
                .map(lwp -> listingMapper.toResponse(lwp.listing(), lwp.property()));
    }

    @Operation(
            summary = "Read a listing's public detail",
            description = """
                    Returns the listing + its associated property (address, type, bedrooms). \
                    Photos, comments, reviews, slots are separate endpoints to keep this \
                    response small enough to cache aggressively.

                    Public — no auth required. Cache-Control headers stamped by the \
                    interceptor. Returns 404 if the listing doesn't exist or has been \
                    administratively taken down.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200",
                    description = "Listing with embedded property snapshot.",
                    content = @Content(schema = @Schema(implementation = ListingResponse.class))),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound")
    })
    @SecurityRequirements // public
    @GetMapping("/{id}")
    public ListingResponse get(
            @Parameter(description = "Listing ID.", example = "17")
            @PathVariable Long id) {
        ListingWithProperty lwp = listingService.findPubliclyVisible(id);
        return listingMapper.toResponse(lwp.listing(), lwp.property(),
                listingService.activeAgentUserId(id),
                listingService.pendingReportCount(id));
    }

    @Operation(
            summary = "Update a listing (price / status / description)",
            description = """
                    Partial update of caller's own listing. Allowed mutations: title, \
                    description, price, status. **Status transitions are state-machine \
                    validated** — `LIVE ↔ PAUSED` is allowed, `LIVE → CLOSED` is allowed, \
                    but `CLOSED → LIVE` is rejected with 409.

                    **Ownership**: only the listing's owner can update. Assigned agents \
                    cannot mutate listing fields directly today (PATCH-via-agent could be a \
                    future enhancement).

                    **Concurrency**: the entity carries `@Version`. A racing PATCH against \
                    the same row produces 409.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200",
                    description = "Listing updated.",
                    content = @Content(schema = @Schema(implementation = ListingResponse.class))),
            @ApiResponse(responseCode = "400", ref = "#/components/responses/ValidationFailed"),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthenticated"),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound"),
            @ApiResponse(responseCode = "409", ref = "#/components/responses/Conflict")
    })
    @SecurityRequirement(name = "bearerAuth")
    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('OWNER')")
    public ListingResponse update(@AuthenticationPrincipal JwtPrincipal principal,
                                  @Parameter(description = "Listing ID.", example = "17")
                                  @PathVariable Long id,
                                  @Valid @RequestBody UpdateListingRequest request) {
        return listingMapper.toResponse(
                listingService.update(principal.userId(), id, request.toCommand()), null);
    }
}
