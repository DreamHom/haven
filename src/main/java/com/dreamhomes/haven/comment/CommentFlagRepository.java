package com.dreamhomes.haven.comment;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommentFlagRepository extends JpaRepository<CommentFlag, Long> {

    Page<CommentFlag> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<CommentFlag> findByStatusOrderByCreatedAtDesc(CommentFlagStatus status, Pageable pageable);

    boolean existsByCommentIdAndReporterUserIdAndStatus(
            Long commentId, Long reporterUserId, CommentFlagStatus status);
}
