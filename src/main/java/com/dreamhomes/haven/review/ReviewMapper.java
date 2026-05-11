package com.dreamhomes.haven.review;

import com.dreamhomes.haven.review.dto.ReviewResponse;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ReviewMapper {
    ReviewResponse toResponse(ListingReview review);
}
