package com.dreamhomes.haven.admin.controller;

import com.dreamhomes.haven.auth.JwtPrincipal;
import com.dreamhomes.haven.listing.dto.ListingResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.dreamhomes.haven.admin.dto.AdminListingResponse;
import com.dreamhomes.haven.admin.dto.TakedownListingRequest;
import com.dreamhomes.haven.admin.service.AdminListingService;

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
        return toAdminResponse(adminListingService.approve(principal.userId(), id));
    }

    @PostMapping("/{id}/takedown")
    public AdminListingResponse takedown(
            @AuthenticationPrincipal JwtPrincipal principal,
            @PathVariable Long id,
            @Valid @RequestBody TakedownListingRequest request) {
        return toAdminResponse(adminListingService.takedown(
                principal.userId(), id, request.reason()));
    }

    private static AdminListingResponse toAdminResponse(ListingResponse l) {
        return new AdminListingResponse(l.id(), l.propertyId(), l.ownerId(),
                l.status(), l.approvedAt(), l.updatedAt());
    }
}
