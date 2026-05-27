package com.dreamhomes.haven.comment;

import com.dreamhomes.haven.auth.JwtPrincipal;
import com.dreamhomes.haven.comment.dto.CommentFlagResponse;
import com.dreamhomes.haven.comment.dto.CommentResponse;
import com.dreamhomes.haven.comment.dto.DeleteCommentRequest;
import com.dreamhomes.haven.comment.dto.FlagCommentRequest;
import com.dreamhomes.haven.comment.dto.PostCommentRequest;
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
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Comment endpoints. Public listing detail pages embed the read endpoint; the post
 * endpoint requires authentication; the delete endpoint authorises in the service.
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "Comments")
public class CommentController {

    private static final int MAX_PAGE_SIZE = 100;

    private final CommentService commentService;
    private final CommentMapper commentMapper;
    private final CommentFlagService commentFlagService;

    @Operation(
            summary = "List comments on a listing",
            description = """
                    Paginated public Q&A on the listing. Soft-deleted rows (`deleted_at` set) \
                    are excluded. Public — no auth required. Cache-Control headers stamped \
                    by the public-cache interceptor.

                    **Threading (Item 8):** each entry carries a `parentCommentId` field. \
                    `null` = top-level comment; non-null = reply to that parent. Vista \
                    builds the threaded tree client-side from the flat list — we deliberately \
                    do not nest server-side so paging stays simple and the wire shape stays flat.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200",
                    description = "Paginated comments. Empty page is valid for a fresh listing.",
                    content = @Content(
                            examples = @ExampleObject(name = "ParentAndReply", value = """
                                    { "content": [
                                        { "id": 5, "listingId": 17, "authorUserId": 89,
                                          "body": "Is the kitchen renovated?",
                                          "parentCommentId": null,
                                          "createdAt": "2026-05-09T18:30:00Z" },
                                        { "id": 7, "listingId": 17, "authorUserId": 42,
                                          "body": "Yes, fully redone in 2024.",
                                          "parentCommentId": 5,
                                          "createdAt": "2026-05-09T18:45:00Z" }
                                      ],
                                      "page": { "size": 20, "number": 0, "totalElements": 2, "totalPages": 1 } }
                                    """))),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound")
    })
    @SecurityRequirements // public
    @GetMapping("/api/listings/{listingId}/comments")
    public Page<CommentResponse> list(
            @Parameter(description = "Listing ID.", example = "17")
            @PathVariable Long listingId,
            @Parameter(description = "Page index (0-based).", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size, capped at 100.", example = "20")
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE));
        return commentService.list(listingId, pageable).map(commentMapper::toResponse);
    }

    @Operation(
            summary = "Post a comment (or reply) on a listing",
            description = """
                    Records a public comment by the authenticated user against the listing. \
                    Any authenticated role (OWNER, AGENT, APPLICANT, ADMIN) can post — \
                    the comment becomes part of the listing's public Q&A.

                    Author identity (`authorUserId`) is taken from the JWT.

                    **Threading (Item 8):** supply `parentCommentId` to post as a reply. \
                    Omit (or pass `null`) for a top-level comment. The parent must exist \
                    (else 404), must not be soft-deleted (else 400), and must belong to the \
                    same listing (else 400). We do not enforce a depth limit at the API; \
                    Vista is expected to cap visual nesting at ~3 levels and present \
                    deeper replies as flat under the level-3 ancestor.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201",
                    description = "Comment (or reply) created.",
                    content = @Content(
                            schema = @Schema(implementation = CommentResponse.class),
                            examples = {
                                @ExampleObject(name = "TopLevelQuestion", value = """
                                        { "id": 6, "listingId": 17, "authorUserId": 89,
                                          "body": "What about parking?",
                                          "parentCommentId": null,
                                          "createdAt": "2026-05-10T10:15:00Z" }
                                        """),
                                @ExampleObject(name = "ReplyToComment", value = """
                                        { "id": 7, "listingId": 17, "authorUserId": 42,
                                          "body": "Yes, fully redone in 2024.",
                                          "parentCommentId": 5,
                                          "createdAt": "2026-05-10T10:16:00Z" }
                                        """)
                            })),
            @ApiResponse(responseCode = "400",
                    description = "Validation failed, or the supplied parentCommentId is invalid"
                            + " (parent is deleted, or belongs to a different listing).",
                    ref = "#/components/responses/ValidationFailed"),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthenticated"),
            @ApiResponse(responseCode = "404",
                    description = "Listing not found, or the supplied parentCommentId points to a non-existent comment.",
                    ref = "#/components/responses/NotFound")
    })
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/api/listings/{listingId}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('OWNER', 'AGENT', 'APPLICANT', 'ADMIN')")
    public CommentResponse post(
            @AuthenticationPrincipal JwtPrincipal principal,
            @Parameter(description = "Listing ID.", example = "17")
            @PathVariable Long listingId,
            @Valid @RequestBody PostCommentRequest request) {
        return commentMapper.toResponse(commentService.post(
                principal.userId(), listingId, request.body(), request.parentCommentId()));
    }

    @Operation(
            summary = "Flag a comment for moderation",
            description = """
                    Opens a moderation flag against a listing comment. Any authenticated \
                    user (OWNER, AGENT, APPLICANT, ADMIN) can flag. The body is optional \
                    — when supplied, the `reason` is shown to admins in the moderation \
                    queue. Flagging the same comment twice as the same reporter while the \
                    first flag is still OPEN returns 409 (partial unique index in Postgres) \
                    so the UI can disable the menu item after the first flag.

                    The path's listing id must match the comment's listing — otherwise 404 \
                    (the comment is treated as not found in that listing's namespace, to \
                    avoid leaking the existence of comments on other listings).

                    **Admin queue.** Flagged rows surface in the admin moderation feed at \
                    `GET /api/admin/comment-flags` (admin-only) so a moderator can \
                    `RESOLVED` (takedown follow-up) or `DISMISSED` (no action) each one.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201",
                    description = "Flag recorded and queued for moderation.",
                    content = @Content(
                            schema = @Schema(implementation = CommentFlagResponse.class),
                            examples = @ExampleObject(name = "OpenFlag", value = """
                                    { "id": 12, "listingId": 17, "commentId": 5,
                                      "reporterUserId": 89, "reason": "spam — selling unrelated services",
                                      "status": "OPEN", "createdAt": "2026-05-10T10:00:00Z" }
                                    """))),
            @ApiResponse(responseCode = "400", ref = "#/components/responses/ValidationFailed"),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthenticated"),
            @ApiResponse(responseCode = "404",
                    description = "Comment not found on this listing (or already hard-deleted).",
                    ref = "#/components/responses/NotFound"),
            @ApiResponse(responseCode = "409",
                    description = "You already have an OPEN flag on this comment — wait for the moderator's decision.",
                    ref = "#/components/responses/Conflict")
    })
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping(value = "/api/listings/{listingId}/comments/{commentId}/flag",
            consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('OWNER', 'AGENT', 'APPLICANT', 'ADMIN')")
    public CommentFlagResponse flag(
            @AuthenticationPrincipal JwtPrincipal principal,
            @Parameter(description = "Listing ID.", example = "17")
            @PathVariable Long listingId,
            @Parameter(description = "Comment ID.", example = "5")
            @PathVariable Long commentId,
            @Valid @RequestBody(required = false) FlagCommentRequest request) {
        String reason = request == null ? null : request.reason();
        return commentFlagService.flag(principal.userId(), listingId, commentId, reason);
    }

    @Operation(
            summary = "Soft-delete a comment",
            description = """
                    Soft-deletes (sets `deleted_at`) — the row is excluded from public reads \
                    but kept for audit. Authorised callers:

                    - The comment's author (anyone removing their own comment).
                    - The listing's owner.
                    - The listing's assigned agent.
                    - Any admin (with optional moderation reason in the body).

                    All other callers receive 403.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Comment soft-deleted."),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthenticated"),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound")
    })
    @SecurityRequirement(name = "bearerAuth")
    @DeleteMapping("/api/comments/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @AuthenticationPrincipal JwtPrincipal principal,
            @Parameter(description = "Comment ID.", example = "5")
            @PathVariable Long id,
            @RequestBody(required = false) DeleteCommentRequest request) {
        String reason = request == null ? null : request.reason();
        commentService.delete(principal.userId(), principal.role(), id, reason);
    }
}
