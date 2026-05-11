package com.dreamhomes.haven.review;

import com.dreamhomes.haven.auth.JwtPrincipal;
import com.dreamhomes.haven.review.dto.DeleteReviewRequest;
import com.dreamhomes.haven.review.dto.PostReviewRequest;
import com.dreamhomes.haven.review.dto.ReviewResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Reviews")
public class ReviewController {

    private static final int MAX_PAGE_SIZE = 100;

    private final ReviewService reviewService;
    private final ReviewMapper reviewMapper;

    @Operation(
            summary = "Post a review on the user the deal closed with",
            description = """
                    Records a 1-5 star review with optional text body. The reviewee can be \
                    the listing's owner OR the listing's assigned agent — whoever you \
                    actually transacted with.

                    **Gating** (enforced server-side):
                    - Listing status must be `CLOSED`.
                    - The caller must have an `ACCEPTED` offer on this listing (you can \
                      only review someone you closed a deal with).
                    - The chosen `revieweeUserId` must be the listing owner or its assigned agent.
                    - At most one review per `(listingId, reviewerUserId, revieweeUserId)` — \
                      duplicates rejected with 409.

                    **Side effect**: the reviewee's profile aggregate (`averageRating`, \
                    `reviewCount`) is updated immediately on every write/delete.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201",
                    description = "Review created and aggregate updated.",
                    content = @Content(
                            schema = @Schema(implementation = ReviewResponse.class),
                            examples = @ExampleObject(name = "FiveStar", value = """
                                    { "id": 14, "listingId": 17,
                                      "reviewerUserId": 89, "revieweeUserId": 23,
                                      "rating": 5, "body": "Responsive and honest. Highly recommended.",
                                      "createdAt": "2026-06-15T14:00:00Z" }
                                    """))),
            @ApiResponse(responseCode = "400", ref = "#/components/responses/ValidationFailed"),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthenticated"),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound"),
            @ApiResponse(responseCode = "409", ref = "#/components/responses/Conflict")
    })
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/api/listings/{listingId}/reviews")
    @ResponseStatus(HttpStatus.CREATED)
    public ReviewResponse post(
            @AuthenticationPrincipal JwtPrincipal principal,
            @Parameter(description = "Listing the deal closed on.", example = "17")
            @PathVariable Long listingId,
            @Valid @RequestBody PostReviewRequest request) {
        return reviewMapper.toResponse(reviewService.post(
                principal.userId(), listingId, request.revieweeUserId(),
                request.rating(), request.body()));
    }

    @Operation(
            summary = "List reviews tied to a listing",
            description = """
                    Paginated reviews on the listing — both directions (owner-reviewing- \
                    applicant and applicant-reviewing-owner). Soft-deleted reviews excluded. \
                    Public — no auth required.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200",
                    description = "Paginated reviews on the listing.",
                    content = @Content(
                            examples = @ExampleObject(name = "OneReview", value = """
                                    { "content": [
                                        { "id": 14, "listingId": 17,
                                          "reviewerUserId": 89, "revieweeUserId": 23,
                                          "rating": 5, "body": "Responsive and honest.",
                                          "createdAt": "2026-06-15T14:00:00Z" }
                                      ],
                                      "page": { "size": 20, "number": 0, "totalElements": 1, "totalPages": 1 } }
                                    """))),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound")
    })
    @SecurityRequirements // public
    @GetMapping("/api/listings/{listingId}/reviews")
    public Page<ReviewResponse> listForListing(
            @Parameter(description = "Listing ID.", example = "17")
            @PathVariable Long listingId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE));
        return reviewService.listForListing(listingId, pageable).map(reviewMapper::toResponse);
    }

    @Operation(
            summary = "List the reviews someone has received",
            description = """
                    Paginated reviews where this user is the **reviewee** — what others \
                    have said about them. Soft-deleted reviews excluded. Public — no auth \
                    required, designed for trust-signal scrutiny before engaging.

                    Pair with `GET /users/{id}/profile` for the aggregate (average rating + count).
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200",
                    description = "Paginated reviews this user has received.",
                    content = @Content(
                            examples = @ExampleObject(name = "AgentReviews", value = """
                                    { "content": [
                                        { "id": 14, "listingId": 17,
                                          "reviewerUserId": 89, "revieweeUserId": 23,
                                          "rating": 5, "body": "Responsive and honest.",
                                          "createdAt": "2026-06-15T14:00:00Z" },
                                        { "id": 9, "listingId": 11,
                                          "reviewerUserId": 41, "revieweeUserId": 23,
                                          "rating": 4, "body": "Smooth process.",
                                          "createdAt": "2026-04-01T11:00:00Z" }
                                      ],
                                      "page": { "size": 20, "number": 0, "totalElements": 2, "totalPages": 1 } }
                                    """))),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound")
    })
    @SecurityRequirements // public
    @GetMapping("/api/users/{userId}/reviews")
    public Page<ReviewResponse> listForUser(
            @Parameter(description = "User ID whose reviews to read.", example = "23")
            @PathVariable Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE));
        return reviewService.listForReviewee(userId, pageable).map(reviewMapper::toResponse);
    }

    @Operation(
            summary = "Soft-delete a review",
            description = """
                    Soft-deletes (sets `deleted_at`). The reviewee's aggregate \
                    (`averageRating`, `reviewCount`) is recomputed accordingly.

                    **Authorisation** (service-enforced):
                    - The review's author can delete their own review.
                    - Any admin can delete any review (with reason — audit-friendly).
                    - All others receive 403.

                    Reason is required.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Review soft-deleted."),
            @ApiResponse(responseCode = "400", ref = "#/components/responses/ValidationFailed"),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthenticated"),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound"),
            @ApiResponse(responseCode = "409", ref = "#/components/responses/Conflict")
    })
    @SecurityRequirement(name = "bearerAuth")
    @DeleteMapping("/api/reviews/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @AuthenticationPrincipal JwtPrincipal principal,
            @Parameter(description = "Review ID.", example = "14")
            @PathVariable Long id,
            @Valid @RequestBody DeleteReviewRequest request) {
        reviewService.delete(principal.userId(), principal.role(), id, request.reason());
    }
}
