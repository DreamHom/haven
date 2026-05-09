package com.dreamhomes.haven.domain.listing.controller;

import com.dreamhomes.haven.domain.listing.dto.ListingResponse;
import com.dreamhomes.haven.domain.listing.service.ListingService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/public/listings")
@RequiredArgsConstructor
public class PublicListingController {

    private final ListingService listingService;

    @GetMapping("/{id}")
    public ListingResponse get(@PathVariable Long id) {
        var l = listingService.get(id);
        return new ListingResponse(l.getId(), l.getPropertyId(), l.getType(), l.getStatus(), l.getPrice(), l.getTitle());
    }
}