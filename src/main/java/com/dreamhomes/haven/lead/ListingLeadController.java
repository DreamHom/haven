package com.dreamhomes.haven.lead;

import com.dreamhomes.haven.auth.JwtPrincipal;
import com.dreamhomes.haven.lead.dto.CreateListingLeadRequest;
import com.dreamhomes.haven.lead.dto.ListingLeadResponse;
import io.swagger.v3.oas.annotations.Operation;
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
    @Operation(summary = "Submit interest on a live listing (contact gated until owner reveal)")
    public ListingLeadResponse submit(
            @PathVariable Long listingId,
            @AuthenticationPrincipal JwtPrincipal principal,
            @Valid @RequestBody CreateListingLeadRequest body) {
        return listingLeadService.submit(listingId, principal.userId(), body);
    }

    @GetMapping
    @PreAuthorize("hasRole('OWNER')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "List leads for a listing you own (paginated, newest first)")
    public Page<ListingLeadResponse> list(
            @PathVariable Long listingId,
            @AuthenticationPrincipal JwtPrincipal principal,
            @PageableDefault(size = 20) Pageable pageable) {
        return listingLeadService.listForListingOwner(listingId, principal.userId(), pageable);
    }

    @PostMapping("/{leadId}/reveal")
    @PreAuthorize("hasRole('OWNER')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Reveal applicant contact details for a lead")
    public ListingLeadResponse reveal(
            @PathVariable Long listingId,
            @PathVariable Long leadId,
            @AuthenticationPrincipal JwtPrincipal principal) {
        return listingLeadService.reveal(listingId, leadId, principal.userId());
    }
}
