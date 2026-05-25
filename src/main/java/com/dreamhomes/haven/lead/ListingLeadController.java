package com.dreamhomes.haven.lead;

import com.dreamhomes.haven.auth.JwtPrincipal;
import com.dreamhomes.haven.lead.dto.CreateListingLeadRequest;
import com.dreamhomes.haven.lead.dto.ListingLeadResponse;
import io.swagger.v3.oas.annotations.Operation;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/listings/{listingId}/leads")
@RequiredArgsConstructor
@Tag(name = "Listing leads")
public class ListingLeadController {

    private final ListingLeadService listingLeadService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('APPLICANT')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            summary = "Submit interest on a live listing (contact gated until owner reveal)",
            description = """
                    **Persona**: Temi (S5), Ngozi (S3) — signals interest without leaking the applicant's
                    contact details until the owner explicitly chooses to reveal them.

                    Creates a lead row tied to the calling applicant + the target listing. Listing must
                    be in LIVE status. The applicant's contact fields stay hidden on the lead-list view
                    until the owner calls the reveal endpoint, which stamps `revealedAt` and surfaces
                    the contact block.

                    Duplicate submission (same applicant on the same listing) returns 409 — applicants
                    can't spam interest.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Lead created."),
            @ApiResponse(responseCode = "400", ref = "#/components/responses/ValidationFailed"),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthenticated"),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound"),
            @ApiResponse(responseCode = "409", ref = "#/components/responses/Conflict")
    })
    public ListingLeadResponse submit(
            @PathVariable Long listingId,
            @AuthenticationPrincipal JwtPrincipal principal,
            @Valid @RequestBody CreateListingLeadRequest body) {
        return listingLeadService.submit(listingId, principal.userId(), body);
    }

    @GetMapping
    @PreAuthorize("hasRole('OWNER')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            summary = "List leads for a listing you own (paginated, newest first)",
            description = """
                    **Persona**: Amaka (S5), Biodun (S5) — owner views the pipeline of interested
                    applicants on a specific listing.

                    Only the listing's owner can fetch this — agents (even ACCEPTED ones) don't get
                    this view today. Contact fields are masked on each row until the owner reveals.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Paginated lead summaries (newest first)."),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthenticated"),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound")
    })
    public Page<ListingLeadResponse> list(
            @PathVariable Long listingId,
            @AuthenticationPrincipal JwtPrincipal principal,
            @PageableDefault(size = 20) Pageable pageable) {
        return listingLeadService.listForListingOwner(listingId, principal.userId(), pageable);
    }

    @PostMapping("/{leadId}/reveal")
    @PreAuthorize("hasRole('OWNER')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            summary = "Reveal applicant contact details for a lead",
            description = """
                    **Persona**: Amaka (S5), Biodun (S5) — owner decides to follow up on a serious
                    lead and chooses to see the applicant's contact.

                    Idempotent: calling reveal on an already-revealed lead returns the same response
                    (no second `revealedAt` mutation). Lead must belong to the listing in the path
                    (cross-listing reveal attempts → 404).
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lead returned with the contact block populated."),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthenticated"),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound")
    })
    public ListingLeadResponse reveal(
            @PathVariable Long listingId,
            @PathVariable Long leadId,
            @AuthenticationPrincipal JwtPrincipal principal) {
        return listingLeadService.reveal(listingId, leadId, principal.userId());
    }
}
