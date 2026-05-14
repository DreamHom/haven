package com.dreamhomes.haven.comment;

import com.dreamhomes.haven.comment.dto.CommentFlagResponse;
import com.dreamhomes.haven.comment.exception.CommentAlreadyDeletedException;
import com.dreamhomes.haven.comment.exception.CommentFlagNotFoundException;
import com.dreamhomes.haven.comment.exception.CommentFlagNotOpenException;
import com.dreamhomes.haven.comment.exception.CommentNotFoundException;
import com.dreamhomes.haven.comment.exception.OpenCommentFlagAlreadyExistsException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
public class CommentFlagService {

    private final CommentFlagRepository commentFlagRepository;
    private final CommentRepository commentRepository;

    @Transactional
    public CommentFlagResponse flag(Long reporterUserId, Long listingId, Long commentId, String reason) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new CommentNotFoundException(commentId));
        if (comment.isDeleted()) {
            throw new CommentAlreadyDeletedException(commentId);
        }
        if (!comment.getListingId().equals(listingId)) {
            throw new CommentNotFoundException(commentId);
        }
        if (commentFlagRepository.existsByCommentIdAndReporterUserIdAndStatus(
                commentId, reporterUserId, CommentFlagStatus.OPEN)) {
            throw new OpenCommentFlagAlreadyExistsException();
        }
        CommentFlag saved;
        try {
            saved = commentFlagRepository.save(CommentFlag.builder()
                    .commentId(commentId)
                    .reporterUserId(reporterUserId)
                    .reason(trimToNull(reason))
                    .status(CommentFlagStatus.OPEN)
                    .build());
            commentFlagRepository.flush();
        } catch (DataIntegrityViolationException ex) {
            throw new OpenCommentFlagAlreadyExistsException();
        }
        log.info("commentFlag id={} commentId={} reporterUserId={}", saved.getId(), commentId, reporterUserId);
        return toResponse(saved, listingId);
    }

    @Transactional(readOnly = true)
    public Page<CommentFlagResponse> adminList(CommentFlagStatus statusFilter, Pageable pageable) {
        Page<CommentFlag> page = statusFilter == null
                ? commentFlagRepository.findAllByOrderByCreatedAtDesc(pageable)
                : commentFlagRepository.findByStatusOrderByCreatedAtDesc(statusFilter, pageable);
        return page.map(f -> toResponse(f, requireListingId(f.getCommentId())));
    }

    @Transactional
    public CommentFlagResponse resolve(Long flagId) {
        return transition(flagId, CommentFlagStatus.RESOLVED);
    }

    @Transactional
    public CommentFlagResponse dismiss(Long flagId) {
        return transition(flagId, CommentFlagStatus.DISMISSED);
    }

    private CommentFlagResponse transition(Long flagId, CommentFlagStatus target) {
        CommentFlag flag = commentFlagRepository.findById(flagId)
                .orElseThrow(() -> new CommentFlagNotFoundException(flagId));
        if (flag.getStatus() != CommentFlagStatus.OPEN) {
            throw new CommentFlagNotOpenException();
        }
        flag.setStatus(target);
        CommentFlag saved = commentFlagRepository.save(flag);
        return toResponse(saved, requireListingId(saved.getCommentId()));
    }

    private Long requireListingId(Long commentId) {
        return commentRepository.findById(commentId)
                .map(Comment::getListingId)
                .orElseThrow(() -> new CommentNotFoundException(commentId));
    }

    private static CommentFlagResponse toResponse(CommentFlag f, Long listingId) {
        return new CommentFlagResponse(
                f.getId(),
                listingId,
                f.getCommentId(),
                f.getReporterUserId(),
                f.getReason(),
                f.getStatus(),
                f.getCreatedAt());
    }

    private static String trimToNull(String reason) {
        if (reason == null) {
            return null;
        }
        String t = reason.trim();
        return t.isEmpty() ? null : t;
    }
}
