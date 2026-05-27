package com.dreamhomes.haven.photo.exception;

import com.dreamhomes.haven.common.DomainException;
import org.springframework.http.HttpStatus;

/**
 * Item 2 — confirm received a {@code fileKey} that doesn't match any intent row. 409
 * (not 404) because the spec covers "never issued" alongside "already confirmed" and
 * "expired" under the same client story: that key isn't usable, request a fresh URL.
 */
public class PhotoUploadIntentNotFoundException extends DomainException {

    public PhotoUploadIntentNotFoundException() {
        super(HttpStatus.CONFLICT,
                "No matching upload intent — request a fresh upload URL");
    }
}
