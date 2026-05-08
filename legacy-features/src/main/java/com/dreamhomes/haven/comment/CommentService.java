package com.dreamhomes.haven.comment;

import com.dreamhomes.haven.listing.Listing;
import com.dreamhomes.haven.listing.ListingNotFoundException;
import com.dreamhomes.haven.listing.ListingRepository;
import com.dreamhomes.haven.notification.Notification;
import com.dreamhomes.haven.notification.NotificationKind;
import com.dreamhomes.haven.notification.NotificationRepository;
import com.dreamhomes.haven.notification.NotificationSource;
import com.dreamhomes.haven.user.Role;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

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
    private final ListingRepository listingRepository;
    private final NotificationRepository notificationRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public Comment post(Long authorId, Long listingId, String body) {
        if (body == null || body.isBlank()) {
            throw new IllegalArgumentException("Comment body cannot be empty");
        }
        Listing listing = listingRepository.findById(listingId)
                .orElseThrow(() -> new ListingNotFoundException(listingId));

        Instant now = Instant.now();
        Comment saved = commentRepository.save(Comment.builder()
                .listingId(listing.getId())
                .authorUserId(authorId)
                .body(body.trim())
                .createdAt(now)
                .build());

        // Self-comments don't notify — owners aren't surprised by their own posts.
        if (!authorId.equals(listing.getOwnerId())) {
            notifyOwner(listing.getOwnerId(), saved);
        }

        log.info("Posted commentId={} listingId={} authorId={}", saved.getId(), listingId, authorId);
        return saved;
    }

    @Transactional(readOnly = true)
    public Page<Comment> list(Long listingId, Pageable pageable) {
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
     *
     * <p>{@code reason} is optional — required for audit-paper-trail clarity if it's an
     * admin or owner takedown, but the API doesn't enforce that here so author self-deletes
     * remain frictionless.
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
        // Listing-owner check: load only when the previous two cheaper checks failed.
        return listingRepository.findById(comment.getListingId())
                .map(l -> callerId.equals(l.getOwnerId()))
                .orElse(false);
    }

    private void notifyOwner(Long ownerId, Comment comment) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("commentId", comment.getId());
        payload.put("listingId", comment.getListingId());
        payload.put("authorUserId", comment.getAuthorUserId());
        notificationRepository.save(Notification.builder()
                .recipientId(ownerId)
                .kind(NotificationKind.COMMENT_POSTED)
                .source(NotificationSource.SYNC)
                .payload(serialize(payload))
                .createdAt(Instant.now())
                .build());
    }

    private String serialize(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialise comment notification payload", e);
        }
    }
}
