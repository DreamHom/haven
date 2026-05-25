package com.dreamhomes.haven.photo.exception;

import com.dreamhomes.haven.common.DomainException;
import org.springframework.http.HttpStatus;

/**
 * Item 2 — caller supplied a {@code contentType} that isn't in the allow-list
 * (image/jpeg, image/png, image/webp). 400 because the input itself is invalid;
 * no R2 round-trip required to know it.
 */
public class PhotoUploadContentTypeNotAllowedException extends DomainException {

    public PhotoUploadContentTypeNotAllowedException(String contentType) {
        super(HttpStatus.BAD_REQUEST,
                "Unsupported content type: " + contentType
                        + ". Allowed: image/jpeg, image/png, image/webp");
    }
}
