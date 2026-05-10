package com.dreamhomes.haven.property;

import com.dreamhomes.haven.auth.JwtPrincipal;
import com.dreamhomes.haven.property.dto.CreatePropertyRequest;
import com.dreamhomes.haven.property.dto.PropertyResponse;
import com.dreamhomes.haven.property.model.Property;
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

@RestController
@RequestMapping("/api/properties")
@RequiredArgsConstructor
@Tag(name = "Properties")
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
        return new PropertyResponse(saved.getId(), saved.getOwnerId(), saved.getType(),
                saved.getAddress(), saved.getBedrooms(), saved.getBathrooms(),
                saved.getSizeSqm(), saved.getDescription(), saved.getCreatedAt());
    }
}
