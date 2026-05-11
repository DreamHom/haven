package com.dreamhomes.haven.photo;

import com.dreamhomes.haven.photo.dto.PhotoResponse;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ListingPhotoMapper {
    PhotoResponse toResponse(ListingPhoto photo);
}
