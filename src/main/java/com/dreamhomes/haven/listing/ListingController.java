package com.dreamhomes.haven.listing;

import com.dreamhomes.haven.auth.JwtPrincipal;
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
public class ListingController {

    private final ListingService listingService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('OWNER')")
    public ListingResponse create(@AuthenticationPrincipal JwtPrincipal principal,
                                  @Valid @RequestBody CreateListingRequest request) {
        return ListingResponse.from(listingService.create(principal.userId(), request.toCommand()));
    }

    @GetMapping
    public Page<ListingResponse> browse(@PageableDefault(size = 20) Pageable pageable) {
        return listingService.browsePublic(pageable).map(ListingResponse::from);
    }

    @GetMapping("/{id}")
    public ListingResponse get(@PathVariable Long id) {
        return ListingResponse.from(listingService.findPubliclyVisible(id));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('OWNER')")
    public ListingResponse update(@AuthenticationPrincipal JwtPrincipal principal,
                                  @PathVariable Long id,
                                  @Valid @RequestBody UpdateListingRequest request) {
        return ListingResponse.from(listingService.update(principal.userId(), id, request.toCommand()));
    }
}
