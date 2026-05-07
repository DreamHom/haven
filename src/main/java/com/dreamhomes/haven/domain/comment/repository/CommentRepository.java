package com.dreamhomes.haven.domain.comment.repository;

import com.dreamhomes.haven.domain.comment.model.Comment;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommentRepository extends JpaRepository<Comment, Long> {
    List<Comment> findByListingIdOrderByCreatedAtDesc(Long listingId);
}

