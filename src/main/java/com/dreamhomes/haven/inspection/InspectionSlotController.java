package com.dreamhomes.haven.inspection;

import com.dreamhomes.haven.auth.JwtPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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

import java.util.List;

@RestController
@RequestMapping("/api/listings/{listingId}/slots")
@RequiredArgsConstructor
public class InspectionSlotController {

    private final InspectionSlotService slotService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('OWNER')")
    public SlotResponse create(@AuthenticationPrincipal JwtPrincipal principal,
                               @PathVariable Long listingId,
                               @Valid @RequestBody CreateSlotRequest request) {
        return SlotResponse.from(slotService.create(principal.userId(), listingId, request.toCommand()));
    }

    @GetMapping
    public List<SlotResponse> listAvailable(@PathVariable Long listingId) {
        return slotService.listAvailableForListing(listingId).stream()
                .map(SlotResponse::from)
                .toList();
    }
}
