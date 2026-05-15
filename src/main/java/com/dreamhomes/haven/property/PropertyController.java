package com.dreamhomes.haven.property;

import com.dreamhomes.haven.auth.JwtPrincipal;
import com.dreamhomes.haven.property.dto.CreatePropertyRequest;
import com.dreamhomes.haven.property.dto.PropertyResponse;
import com.dreamhomes.haven.property.dto.UpdatePropertyRequest;
import com.dreamhomes.haven.property.exception.PropertyNotFoundException;
import com.dreamhomes.haven.property.model.Property;
import com.dreamhomes.haven.user.model.Role;
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
@RequestMapping("/api/properties")
@RequiredArgsConstructor
@Tag(name = "Properties")
@org.springframework.validation.annotation.Validated
public class PropertyController {

    private final PropertyService propertyService;

    @Operation(
            summary = "Register a new property",
            description = """
                    Creates a `Property` row owned by the calling user. The property is the \
                    physical asset; one or more `Listing`s can be created against it later \
                    (rent now, sell later, re-list after a tenant turnover — same property, \
                    multiple listings over time).

                    **Ownership**: `ownerId` is taken from the JWT, never from the request body. \
                    A caller cannot create a property "for" another user.

                    **Role gate**: `OWNER` only. APPLICANT and AGENT receive 403.

                    Optional **map pin**: `latitude` and `longitude` (WGS-84 decimals) — both \
                    must be sent together or both omitted.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201",
                    description = "Property created.",
                    content = @Content(
                            schema = @Schema(implementation = PropertyResponse.class),
                            examples = @ExampleObject(name = "ApartmentInLekki", value = """
                                    { "id": 42, "ownerId": 7, "type": "APARTMENT",
                                      "address": "12B Admiralty Way, Lekki Phase 1, Lagos",
                                      "bedrooms": 3, "bathrooms": 2, "sizeSqm": 145,
                                      "description": "Top-floor apartment with sea-view balcony.",
                                      "latitude": 6.4541, "longitude": 3.3947,
                                      "createdAt": "2026-05-10T08:30:00Z" }
                                    """))),
            @ApiResponse(responseCode = "400", ref = "#/components/responses/ValidationFailed"),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthenticated"),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden")
    })
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('OWNER')")
    public PropertyResponse create(@AuthenticationPrincipal JwtPrincipal principal,
                                   @Valid @RequestBody CreatePropertyRequest request) {
        Property saved = propertyService.create(principal.userId(), request.toCommand());
        return toResponse(saved);
    }

    @Operation(
            summary = "Register many properties in one call",
            description = """
                    Bulk variant of {@code POST /api/properties}. Persona audit (Biodun): a
                    developer onboarding a 60-unit tower shouldn't fire 60 sequential HTTP
                    calls. Each item is validated independently; the whole batch is one
                    transaction — if any single item fails validation the whole call rolls
                    back. Responses come back in the same order as the request.

                    **Limits**: at most 100 properties per call. Body shape is a JSON array
                    of {@link CreatePropertyRequest} objects.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "All properties created."),
            @ApiResponse(responseCode = "400", ref = "#/components/responses/ValidationFailed"),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthenticated"),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden")
    })
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/bulk")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('OWNER')")
    public java.util.List<PropertyResponse> createBulk(
            @AuthenticationPrincipal JwtPrincipal principal,
            @Valid @RequestBody @jakarta.validation.constraints.Size(min = 1, max = 100)
            java.util.List<CreatePropertyRequest> requests) {
        java.util.List<PropertyResponse> out = new java.util.ArrayList<>(requests.size());
        for (CreatePropertyRequest r : requests) {
            out.add(toResponse(propertyService.create(principal.userId(), r.toCommand())));
        }
        return out;
    }

    private static PropertyResponse toResponse(Property saved) {
        return new PropertyResponse(saved.getId(), saved.getOwnerId(), saved.getType(),
                saved.getAddress(), saved.getBedrooms(), saved.getBathrooms(),
                saved.getSizeSqm(), saved.getDescription(),
                saved.getLatitude(), saved.getLongitude(),
                saved.getCreatedAt());
    }

    @Operation(
            summary = "List my properties",
            description = """
                    Returns the caller's own properties, newest first. The persona audit
                    (Amaka, Biodun) flagged this gap: once an owner forgets a property ID
                    from the create response, there's no other way to find it.

                    **Role gate**: `OWNER` only.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Paginated list of the caller's properties."),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthenticated"),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden")
    })
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/mine")
    @PreAuthorize("hasRole('OWNER')")
    public Page<PropertyResponse> listMine(@AuthenticationPrincipal JwtPrincipal principal,
                                           @PageableDefault(size = 20) Pageable pageable) {
        return propertyService.listMine(principal.userId(), pageable);
    }

    @Operation(
            summary = "Read a property by ID",
            description = """
                    Returns the property if the caller owns it (or is an admin). Returns 404
                    to non-owners to avoid leaking existence. Use this to look up properties
                    you created earlier — combined with `GET /api/properties/mine` it closes
                    the "I created a property and lost the ID" gap.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200",
                    description = "Property detail.",
                    content = @Content(schema = @Schema(implementation = PropertyResponse.class))),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthenticated"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound")
    })
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/{id}")
    public PropertyResponse get(@AuthenticationPrincipal JwtPrincipal principal,
                                @Parameter(description = "Property ID.", example = "42")
                                @PathVariable Long id) {
        // 404 (not 403) for non-owners to avoid leaking existence — same pattern
        // as findPubliclyVisible on listings.
        Long owner = propertyService.ownerOf(id).orElseThrow(() -> new PropertyNotFoundException(id));
        if (!owner.equals(principal.userId()) && principal.role() != Role.ADMIN) {
            throw new PropertyNotFoundException(id);
        }
        return propertyService.findById(id);
    }

    @Operation(
            summary = "Update a property (partial)",
            description = """
                    Partial update of address, room counts, size, description, and/or map \
                    coordinates. **Type is immutable** on this endpoint — an apartment stays \
                    an apartment.

                    **Authorisation**: the property owner, or an `ADMIN`, may patch. Anyone \
                    else receives `404` (existence is not leaked to non-owners).

                    **Room counts**: after the patch, the same rules as create apply — e.g. \
                    `APARTMENT` must have both `bedrooms` and `bathrooms` set (either carried \
                    over from before or supplied in this request).

                    **Coordinates**: `latitude` and `longitude` must be sent together.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200",
                    description = "Property updated.",
                    content = @Content(schema = @Schema(implementation = PropertyResponse.class))),
            @ApiResponse(responseCode = "400", ref = "#/components/responses/ValidationFailed"),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthenticated"),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound")
    })
    @SecurityRequirement(name = "bearerAuth")
    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('OWNER') or hasRole('ADMIN')")
    public PropertyResponse update(@AuthenticationPrincipal JwtPrincipal principal,
                                   @Parameter(description = "Property ID.", example = "42")
                                   @PathVariable Long id,
                                   @Valid @RequestBody UpdatePropertyRequest request) {
        return propertyService.update(principal.userId(), principal.role(), id, request.toCommand());
    }
}
