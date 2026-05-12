package com.dreamhomes.haven.comment;

import com.dreamhomes.haven.listing.ListingService;
import com.dreamhomes.haven.listing.exception.ListingNotFoundException;
import com.dreamhomes.haven.listing.dto.ListingResponse;
import com.dreamhomes.haven.notification.NotificationApi;
import com.dreamhomes.haven.notification.model.NotificationKind;
import com.dreamhomes.haven.user.model.Role;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import com.dreamhomes.haven.comment.exception.CommentAlreadyDeletedException;
import com.dreamhomes.haven.comment.exception.CommentNotFoundException;
import com.dreamhomes.haven.comment.exception.NotAuthorisedToDeleteCommentException;
import com.dreamhomes.haven.listing.model.Listing;

/**
 * Public Q&A on listings (PRD §4.9). Posts are sync; deletes are soft. Owner of the
 * listing, the comment's author, and admins can each delete; the rule lives here, not in
 * the controller, so future callers (admin moderation tooling, batch ops) inherit it.
 *
 * <p>A comment by anyone other than the listing owner fires a sync
 * {@link NotificationKind#COMMENT_POSTED} notification — owners need to know someone
 * asked something on their listing without polling.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class CommentService {

    private final CommentRepository commentRepository;
    private final ListingService listingService;
    private final NotificationApi notificationApi;

    @Transactional
    public Comment post(Long authorId, Long listingId, String body) {
        if (body == null || body.isBlank()) {
            throw new IllegalArgumentException("Comment body cannot be empty");
        }
        // Throws ListingNotFoundException if missing.
        ListingResponse listing = listingService.findById(listingId);

        Comment saved = commentRepository.save(Comment.builder()
                .listingId(listing.id())
                .authorUserId(authorId)
                .body(body.trim())
                .build());

        // Self-comments don't notify — owners aren't surprised by their own posts.
        if (!authorId.equals(listing.ownerId())) {
            notifyOwner(listing.ownerId(), saved);
        }

        log.info("Posted commentId={} listingId={} authorId={}", saved.getId(), listingId, authorId);
        return saved;
    }

    @Transactional(readOnly = true)
    public Page<Comment> list(Long listingId, Pageable pageable) {
        // 404 if the listing is missing — see B-2 in the persona audit.
        if (!listingService.exists(listingId)) {
            throw new com.dreamhomes.haven.listing.exception.ListingNotFoundException(listingId);
        }
        return commentRepository.findByListingIdAndDeletedAtIsNullOrderByCreatedAtAsc(listingId, pageable);
    }

    /**
     * Soft-delete. Authorisation rule:
     * <ul>
     *   <li>Caller is the comment's author, OR</li>
     *   <li>Caller is the listing's owner, OR</li>
     *   <li>Caller has role {@link Role#ADMIN}.</li>
     * </ul>
     * Anything else → 403 with no information about which condition failed (don't leak
     * comment-author identity to randos).
     */
    @Transactional
    public Comment delete(Long callerId, Role callerRole, Long commentId, String reason) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new CommentNotFoundException(commentId));
        if (comment.isDeleted()) {
            throw new CommentAlreadyDeletedException(commentId);
        }
        if (!isAuthorisedToDelete(callerId, callerRole, comment)) {
            throw new NotAuthorisedToDeleteCommentException();
        }

        comment.setDeletedAt(Instant.now());
        comment.setDeletedByUserId(callerId);
        comment.setDeletionReason(reason);
        commentRepository.save(comment);

        log.info("Deleted commentId={} by callerId={} role={}", commentId, callerId, callerRole);
        return comment;
    }

    private boolean isAuthorisedToDelete(Long callerId, Role callerRole, Comment comment) {
        if (callerRole == Role.ADMIN) {
            return true;
        }
        if (callerId.equals(comment.getAuthorUserId())) {
            return true;
        }
        // Listing-owner check: API call only when the previous two cheaper checks failed.
        return listingService.isOwnedBy(comment.getListingId(), callerId);
    }

    private void notifyOwner(Long ownerId, Comment comment) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("commentId", comment.getId());
        payload.put("listingId", comment.getListingId());
        payload.put("authorUserId", comment.getAuthorUserId());
        notificationApi.recordSync(NotificationKind.COMMENT_POSTED, ownerId, payload);
    }

    /**
     * Touched only to keep the import alignment when ListingService.findById gains an
     * Optional overload. Currently throws inside the API on miss.
     */
    @SuppressWarnings("unused")
    private static void touchExceptionImport(ListingNotFoundException e) {
        // never called.
    }
}
