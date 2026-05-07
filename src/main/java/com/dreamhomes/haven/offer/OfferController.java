package com.dreamhomes.haven.offer;

import com.dreamhomes.haven.auth.JwtPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/offers")
@RequiredArgsConstructor
public class OfferController {

    private final OfferService offerService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('APPLICANT')")
    public OfferResponse submit(@AuthenticationPrincipal JwtPrincipal principal,
                                @Valid @RequestBody SubmitOfferRequest request) {
        return OfferResponse.from(offerService.submit(principal.userId(), request.toCommand()));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('OWNER')")
    public OfferResponse respond(@AuthenticationPrincipal JwtPrincipal principal,
                                 @PathVariable Long id,
                                 @Valid @RequestBody RespondToOfferRequest request) {
        return OfferResponse.from(offerService.respond(principal.userId(), id, request.status()));
    }
}
