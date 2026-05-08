package com.dreamhomes.haven.photo;

import com.dreamhomes.haven.auth.JwtPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class ListingPhotoController {

    private final ListingPhotoService listingPhotoService;

    @PostMapping("/api/listings/{listingId}/photos")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('OWNER')")
    public PhotoResponse add(
            @AuthenticationPrincipal JwtPrincipal principal,
            @PathVariable Long listingId,
            @Valid @RequestBody AddPhotoRequest request) {
        return PhotoResponse.from(listingPhotoService.add(
                principal.userId(), listingId, request.url(), request.caption()));
    }

    @DeleteMapping("/api/listings/photos/{photoId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('OWNER')")
    public void delete(
            @AuthenticationPrincipal JwtPrincipal principal,
            @PathVariable Long photoId) {
        listingPhotoService.delete(principal.userId(), photoId);
    }

    @GetMapping("/api/listings/{listingId}/photos")
    public List<PhotoResponse> list(@PathVariable Long listingId) {
        return listingPhotoService.list(listingId).stream().map(PhotoResponse::from).toList();
    }
}
