package com.dreamhomes.haven.comment;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommentRepository extends JpaRepository<Comment, Long> {

    Page<Comment> findByListingIdAndDeletedAtIsNullOrderByCreatedAtAsc(Long listingId, Pageable pageable);
}
