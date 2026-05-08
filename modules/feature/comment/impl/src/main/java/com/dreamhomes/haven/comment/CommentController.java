package com.dreamhomes.haven.comment;

import com.dreamhomes.haven.auth.JwtPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Comment endpoints. Public listing detail pages embed the read endpoint; the post
 * endpoint requires authentication; the delete endpoint authorises in the service.
 */
@RestController
@RequiredArgsConstructor
public class CommentController {

    private static final int MAX_PAGE_SIZE = 100;

    private final CommentService commentService;

    @GetMapping("/api/listings/{listingId}/comments")
    public Page<CommentResponse> list(
            @PathVariable Long listingId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE));
        return commentService.list(listingId, pageable).map(CommentController::toResponse);
    }

    @PostMapping("/api/listings/{listingId}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('OWNER', 'AGENT', 'APPLICANT', 'ADMIN')")
    public CommentResponse post(
            @AuthenticationPrincipal JwtPrincipal principal,
            @PathVariable Long listingId,
            @Valid @RequestBody PostCommentRequest request) {
        return toResponse(commentService.post(principal.userId(), listingId, request.body()));
    }

    static CommentResponse toResponse(Comment c) {
        return new CommentResponse(c.getId(), c.getListingId(), c.getAuthorUserId(),
                c.getBody(), c.getCreatedAt());
    }

    @DeleteMapping("/api/comments/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @AuthenticationPrincipal JwtPrincipal principal,
            @PathVariable Long id,
            @RequestBody(required = false) DeleteCommentRequest request) {
        String reason = request == null ? null : request.reason();
        commentService.delete(principal.userId(), principal.role(), id, reason);
    }
}
