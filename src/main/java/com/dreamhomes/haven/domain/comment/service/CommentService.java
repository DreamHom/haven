package com.dreamhomes.haven.domain.comment.service;

import com.dreamhomes.haven.domain.comment.dto.CreateCommentRequest;
import com.dreamhomes.haven.domain.comment.model.Comment;
import com.dreamhomes.haven.domain.comment.repository.CommentRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CommentService {
    private final CommentRepository commentRepository;

    public CommentService(CommentRepository commentRepository) {
        this.commentRepository = commentRepository;
    }

    @Transactional
    public Comment create(CreateCommentRequest req) {
        var c = new Comment();
        c.setListingId(req.listingId());
        c.setUserId(req.userId());
        c.setBody(req.body());
        return commentRepository.save(c);
    }

    @Transactional(readOnly = true)
    public List<Comment> listByListing(Long listingId) {
        return commentRepository.findByListingIdOrderByCreatedAtDesc(listingId);
    }
}

