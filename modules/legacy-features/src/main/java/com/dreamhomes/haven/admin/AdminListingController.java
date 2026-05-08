package com.dreamhomes.haven.admin;

import com.dreamhomes.haven.auth.JwtPrincipal;
import com.dreamhomes.haven.listing.Listing;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/listings")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminListingController {

    private final AdminListingService adminListingService;

    @PostMapping("/{id}/approve")
    public AdminListingResponse approve(
            @AuthenticationPrincipal JwtPrincipal principal,
            @PathVariable Long id) {
        Listing approved = adminListingService.approve(principal.userId(), id);
        return AdminListingResponse.from(approved);
    }

    @PostMapping("/{id}/takedown")
    public AdminListingResponse takedown(
            @AuthenticationPrincipal JwtPrincipal principal,
            @PathVariable Long id,
            @Valid @RequestBody TakedownListingRequest request) {
        Listing closed = adminListingService.takedown(
                principal.userId(), id, request.reason());
        return AdminListingResponse.from(closed);
    }
}
