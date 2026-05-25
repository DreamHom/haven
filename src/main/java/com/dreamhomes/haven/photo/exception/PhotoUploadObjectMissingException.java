package com.dreamhomes.haven.photo.exception;

import com.dreamhomes.haven.common.DomainException;
import org.springframework.http.HttpStatus;

/**
 * Item 2 — caller invoked /confirm but a HEAD against R2 reported the object isn't
 * there. 422 (Unprocessable Entity): the request was syntactically valid but the
 * world it references is missing — the canonical 4xx for "your input is sound but
 * cannot be processed in the current state".
 */
public class PhotoUploadObjectMissingException extends DomainException {

    public PhotoUploadObjectMissingException(String fileKey) {
        super(HttpStatus.UNPROCESSABLE_ENTITY,
                "Object " + fileKey + " was not found in storage — was the PUT successful?");
    }
}
