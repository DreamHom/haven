package com.dreamhomes.haven.review;

import com.dreamhomes.haven.auth.JwtPrincipal;
import jakarta.validation.Valid;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class ReviewController {

    private static final int MAX_PAGE_SIZE = 100;

    private final ReviewService reviewService;

    @PostMapping("/api/listings/{listingId}/reviews")
    @ResponseStatus(HttpStatus.CREATED)
    public ReviewResponse post(
            @AuthenticationPrincipal JwtPrincipal principal,
            @PathVariable Long listingId,
            @Valid @RequestBody PostReviewRequest request) {
        return toResponse(reviewService.post(
                principal.userId(), listingId, request.revieweeUserId(),
                request.rating(), request.body()));
    }

    static ReviewResponse toResponse(ListingReview r) {
        return new ReviewResponse(r.getId(), r.getListingId(),
                r.getReviewerUserId(), r.getRevieweeUserId(),
                r.getRating(), r.getBody(), r.getCreatedAt());
    }

    @GetMapping("/api/listings/{listingId}/reviews")
    public Page<ReviewResponse> listForListing(
            @PathVariable Long listingId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE));
        return reviewService.listForListing(listingId, pageable).map(ReviewController::toResponse);
    }

    @GetMapping("/api/users/{userId}/reviews")
    public Page<ReviewResponse> listForUser(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE));
        return reviewService.listForReviewee(userId, pageable).map(ReviewController::toResponse);
    }

    /**
     * Soft-delete a review. Author OR admin can call; the service does the role check.
     * Reason is required (audit-friendly).
     */
    @DeleteMapping("/api/reviews/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @AuthenticationPrincipal JwtPrincipal principal,
            @PathVariable Long id,
            @Valid @RequestBody DeleteReviewRequest request) {
        reviewService.delete(principal.userId(), principal.role(), id, request.reason());
    }
}
