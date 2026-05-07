package com.dreamhomes.haven.domain.listing.controller;

import com.dreamhomes.haven.domain.listing.dto.CreateListingRequest;
import com.dreamhomes.haven.domain.listing.dto.ListingResponse;
import com.dreamhomes.haven.domain.listing.dto.UpdateListingRequest;
import com.dreamhomes.haven.domain.listing.service.ListingService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/listings")
public class ListingController {
    private final ListingService listingService;

    public ListingController(ListingService listingService) {
        this.listingService = listingService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ListingResponse create(@Valid @RequestBody CreateListingRequest req) {
        var l = listingService.create(req);
        return new ListingResponse(l.getId(), l.getPropertyId(), l.getType(), l.getStatus(), l.getPrice(), l.getTitle());
    }

    @GetMapping("/{id}")
    public ListingResponse get(@PathVariable Long id) {
        var l = listingService.get(id);
        return new ListingResponse(l.getId(), l.getPropertyId(), l.getType(), l.getStatus(), l.getPrice(), l.getTitle());
    }

    @PutMapping("/{id}")
    public ListingResponse update(@PathVariable Long id, @RequestBody UpdateListingRequest req) {
        var l = listingService.update(id, req);
        return new ListingResponse(l.getId(), l.getPropertyId(), l.getType(), l.getStatus(), l.getPrice(), l.getTitle());
    }
}

