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
@RequiredArgsConstructor
@Tag(name = "Listings")
public class ListingController {

    private final ListingService listingService;

    @Operation(
            summary = "Publish a listing",
            description = """
                    Creates a `Listing` against one of the caller's properties at the chosen \
                    type (`RENT` / `SALE`) and price. The listing starts in `OPEN` status — \
                    publicly browsable immediately, no admin pre-approval gate. Admin only \
                    intervenes reactively via takedown.

                    **Ownership**: the caller must own the target `Property` (FK check). \
                    Trying to list someone else's property returns 403.

                    **Role gate**: `OWNER` only. Agents publish via the agent-assignment flow.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201",
                    description = "Listing created in OPEN.",
                    content = @Content(
                            schema = @Schema(implementation = ListingResponse.class),
                            examples = @ExampleObject(name = "OpenRental", value = """
                                    { "id": 17, "propertyId": 42, "ownerId": 7,
                                      "type": "RENT", "price": 850000, "currency": "NGN",
                                      "status": "OPEN", "title": "3-bed apartment, Lekki",
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
        return ListingService.toResponse(
                listingService.create(principal.userId(), request.toCommand()), null);
    }

    @Operation(
            summary = "Browse public listings",
            description = """
                    Paginated list of publicly-visible listings (status `OPEN`, not \
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
                                          "status": "OPEN", "title": "3-bed apartment, Lekki",
                                          "createdAt": "2026-05-10T08:30:00Z" }
                                      ],
                                      "page": { "size": 20, "number": 0, "totalElements": 1, "totalPages": 1 } }
                                    """)))
    })
    @SecurityRequirements // public
    @GetMapping
    public Page<ListingResponse> browse(
            @Parameter(description = "Standard Spring pagination — defaults to page=0&size=20.")
            @PageableDefault(size = 20) Pageable pageable) {
        return listingService.browsePublic(pageable)
                .map(lwp -> ListingService.toResponse(lwp.listing(), lwp.property()));
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
        return ListingService.toResponse(lwp.listing(), lwp.property());
    }

    @Operation(
            summary = "Update a listing (price / status / description)",
            description = """
                    Partial update of caller's own listing. Allowed mutations: title, \
                    description, price, status. **Status transitions are state-machine \
                    validated** — `OPEN ↔ PAUSED` is allowed, `OPEN → CLOSED` is allowed, \
                    but `CLOSED → OPEN` is rejected with 409.

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
        return ListingService.toResponse(
                listingService.update(principal.userId(), id, request.toCommand()), null);
    }
}
