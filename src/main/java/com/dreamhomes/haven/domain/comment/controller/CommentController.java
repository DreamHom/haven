package com.dreamhomes.haven.domain.comment.controller;

import com.dreamhomes.haven.domain.comment.dto.CommentResponse;
import com.dreamhomes.haven.domain.comment.dto.CreateCommentRequest;
import com.dreamhomes.haven.domain.comment.service.CommentService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/comments")
public class CommentController {

    private final CommentService commentService;

    public CommentController(CommentService commentService) {
        this.commentService = commentService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CommentResponse create(@Valid @RequestBody CreateCommentRequest req) {
        var c = commentService.create(req);
        return new CommentResponse(c.getId(), c.getListingId(), c.getUserId(), c.getBody(), c.getCreatedAt());
    }

    @GetMapping
    public List<CommentResponse> list(@RequestParam Long listingId) {
        return commentService.listByListing(listingId).stream()
                .map(c -> new CommentResponse(c.getId(), c.getListingId(), c.getUserId(), c.getBody(), c.getCreatedAt()))
                .toList();
    }
}

