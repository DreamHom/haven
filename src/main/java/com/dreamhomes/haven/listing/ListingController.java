package com.dreamhomes.haven.listing;

import com.dreamhomes.haven.auth.JwtPrincipal;
import com.dreamhomes.haven.listing.dto.CreateListingRequest;
import com.dreamhomes.haven.listing.dto.ListingResponse;
import com.dreamhomes.haven.listing.dto.ListingWithProperty;
import com.dreamhomes.haven.listing.dto.UpdateListingRequest;
import com.dreamhomes.haven.listing.model.Listing;
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

                    **One LIVE per (property, listing_type) — Item 12.** A property can carry \
                    at most one LIVE `RENT` listing and at most one LIVE `SALE` listing at any \
                    moment. The two coexist (rent + sale on the same home is legitimate), but \
                    a second LIVE listing of the same type on the same property is rejected \
                    with `409 Conflict` and the type URI suffix \
                    `listing.duplicate-open-listing-for-property-and-type`. Close (or pause) \
                    the existing one first. Enforced by both a service-level pre-check and a \
                    Postgres partial unique index (V47) — the second is the race-safety net.

                    Optional marketing: `virtualTourUrl` (max 2048 chars), `priceNegotiable` \
                    (defaults to `false` when omitted). The response includes `ownerPublicBio` \
                    when the owner has set `publicBio` on their account (`PATCH /api/me`).
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
            @ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound"),
            @ApiResponse(responseCode = "409",
                    description = "Either an optimistic-lock conflict, or — per Item 12 — the "
                            + "property already has a LIVE listing of the same `listing_type`. "
                            + "ProblemDetail `type` URI suffix "
                            + "`listing.duplicate-open-listing-for-property-and-type` "
                            + "distinguishes the duplicate-LIVE case.",
                    content = @Content(
                            examples = @ExampleObject(name = "DuplicateOpenListing", value = """
                                    { "type": "https://github.com/DreamHom/haven/blob/main/docs/errors/listing.duplicate-open-listing-for-property-and-type",
                                      "title": "Conflict",
                                      "status": 409,
                                      "detail": "Property 42 already has an active RENT listing — close it before publishing a new one" }
                                    """)))
    })
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('OWNER')")
    public ListingResponse create(@AuthenticationPrincipal JwtPrincipal principal,
                                  @Valid @RequestBody CreateListingRequest request) {
        Listing saved =
                listingService.create(principal.userId(), request.toCommand());
        return listingResponseWithOwnerBio(saved);
    }

    @Operation(
            summary = "Publish many listings in one call",
            description = """
                    Bulk variant of {@code POST /api/listings}. Persona audit (Biodun): a
                    developer publishing the same tower across 60 units shouldn't fan out
                    60 individual calls and reconcile partial state. One transaction — if
                    any single listing fails validation or ownership check the whole batch
                    rolls back. Responses come back in the same order.

                    **Limits**: at most 100 listings per call. Each response item includes \
                    `ownerPublicBio` when the owner has set `publicBio` on their account.
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
            out.add(listingResponseWithOwnerBio(
                    listingService.create(principal.userId(), r.toCommand())));
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

                    **Trust signal rendering (Item 16, post-session-tasks.md).** Each \
                    `ListingResponse` carries two timestamps the UI uses to render trust \
                    chips on the card without any follow-up fetch:

                    | Owner identity verified? | Property docs verified? | UI signal |
                    |---|---|---|
                    | `ownerIdentityVerifiedAt == null` | (any) | "⚠️ Possible Scam" warning chip |
                    | non-null | `property.documentsVerifiedAt == null` | no chip (baseline) |
                    | non-null | non-null | "✓ Verified" badge |

                    Both chips can appear together on the rare card where the owner is \
                    unverified but the property docs got verified separately.
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
                .map(lwp -> listingMapper.toResponse(lwp.listing(), lwp.property(), null, null,
                        lwp.ownerPublicBio(), lwp.ownerIdentityVerifiedAt()));
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
                .map(lwp -> listingMapper.toResponse(lwp.listing(), lwp.property(), null, null,
                        lwp.ownerPublicBio(), lwp.ownerIdentityVerifiedAt()));
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

                    **Trust signal rendering (Item 16, post-session-tasks.md).** The \
                    response carries two trust-signal timestamps so detail pages can render \
                    the chip alongside the listing without an N+1 fetch:

                    | Owner identity verified? | Property docs verified? | UI signal |
                    |---|---|---|
                    | `ownerIdentityVerifiedAt == null` | (any) | "⚠️ Possible Scam" warning chip |
                    | non-null | `property.documentsVerifiedAt == null` | no chip (baseline) |
                    | non-null | non-null | "✓ Verified" badge |
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
        listingService.recordPublicView(id);
        return listingMapper.toResponse(lwp.listing(), lwp.property(),
                listingService.activeAgentUserId(id),
                listingService.pendingReportCount(id),
                lwp.ownerPublicBio(),
                lwp.ownerIdentityVerifiedAt());
    }

    @Operation(
            summary = "Update a listing (price / status / description)",
            description = """
                    Partial update of caller's own listing. Allowed mutations: title, \
                    description, headline, handoverDate, `virtualTourUrl`, `priceNegotiable`, \
                    price, status. **Status transitions are state-machine \
                    validated** — `LIVE ↔ PAUSED` is allowed, `LIVE → CLOSED` is allowed, \
                    but `CLOSED → LIVE` is rejected with 409.

                    **Ownership**: only the listing's owner can update. Assigned agents \
                    cannot mutate listing fields directly today (PATCH-via-agent could be a \
                    future enhancement).

                    **Concurrency**: the entity carries `@Version`. A racing PATCH against \
                    the same row produces 409.

                    **One LIVE per (property, listing_type) — Item 12.** Transitioning a \
                    PAUSED / CLOSED-and-reopened-via-admin listing back to `LIVE` is blocked \
                    with `409 Conflict` (type URI suffix \
                    `listing.duplicate-open-listing-for-property-and-type`) if the property \
                    already has a sibling LIVE listing of the same `listing_type`. Close (or \
                    pause) the existing one first.

                    **Response**: includes `ownerPublicBio` when the listing owner has set \
                    `publicBio` (`PATCH /api/me`).
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
    @PreAuthorize("hasAnyRole('OWNER', 'AGENT')")
    public ListingResponse update(@AuthenticationPrincipal JwtPrincipal principal,
                                  @Parameter(description = "Listing ID.", example = "17")
                                  @PathVariable Long id,
                                  @Valid @RequestBody UpdateListingRequest request) {
        Listing saved =
                listingService.update(principal.userId(), principal.role(), id, request.toCommand());
        return listingResponseWithOwnerBio(saved);
    }

    private ListingResponse listingResponseWithOwnerBio(Listing listing) {
        ListingService.OwnerTrustSnapshot trust =
                listingService.findOwnerTrust(listing.getOwnerId());
        return listingMapper.toResponse(listing, null, null, null,
                trust.publicBio(), trust.identityVerifiedAt());
    }
}
