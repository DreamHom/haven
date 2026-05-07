package com.dreamhomes.haven.engagement;

import com.dreamhomes.haven.auth.JwtPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class ListingSaveController {

    private static final int MAX_PAGE_SIZE = 100;

    private final ListingSaveService listingSaveService;

    @PostMapping("/api/listings/{listingId}/save")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void save(
            @AuthenticationPrincipal JwtPrincipal principal,
            @PathVariable Long listingId) {
        listingSaveService.save(principal.userId(), listingId);
    }

    @DeleteMapping("/api/listings/{listingId}/save")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unsave(
            @AuthenticationPrincipal JwtPrincipal principal,
            @PathVariable Long listingId) {
        listingSaveService.unsave(principal.userId(), listingId);
    }

    @GetMapping("/api/saves/mine")
    public Page<ListingSave> listMine(
            @AuthenticationPrincipal JwtPrincipal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE));
        return listingSaveService.listMine(principal.userId(), pageable);
    }
}
