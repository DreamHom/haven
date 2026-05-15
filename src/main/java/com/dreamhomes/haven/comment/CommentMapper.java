package com.dreamhomes.haven.comment;

import com.dreamhomes.haven.comment.dto.CommentResponse;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface CommentMapper {
    CommentResponse toResponse(Comment comment);
}
