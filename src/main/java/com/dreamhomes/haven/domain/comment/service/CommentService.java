package com.dreamhomes.haven.domain.comment.service;

import com.dreamhomes.haven.domain.comment.dto.CreateCommentRequest;
import com.dreamhomes.haven.domain.comment.model.Comment;
import com.dreamhomes.haven.domain.comment.repository.CommentRepository;
import com.dreamhomes.haven.exception.ValidationException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CommentService {
    private final CommentRepository commentRepository;

    @Transactional
    public Comment create(CreateCommentRequest req) {
        var c = new Comment();
        c.setListingId(req.listingId());
        c.setUserId(req.userId());
        c.setBody(req.body());
        return commentRepository.save(c);
    }

    @Transactional(readOnly = true)
    public List<Comment> listByListing(Long listingId, String order) {
        var direction = parseDirection(order);
        return commentRepository.findByListingId(listingId, Sort.by(direction, "createdAt"));
    }

    private Sort.Direction parseDirection(String order) {
        try {
            return Sort.Direction.fromString(order);
        } 
        
        catch (IllegalArgumentException ex) {
            throw new ValidationException("order must be either 'asc' or 'desc'");
        }
    }
}