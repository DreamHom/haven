package com.dreamhomes.haven.photo.exception;

import com.dreamhomes.haven.common.DomainException;
import org.springframework.http.HttpStatus;

/**
 * Item 2 — confirm called after the URL's expiry. 409 matches the "already confirmed"
 * + "never issued" siblings: caller's recovery path is the same — request a fresh URL.
 */
public class PhotoUploadIntentExpiredException extends DomainException {

    public PhotoUploadIntentExpiredException() {
        super(HttpStatus.CONFLICT,
                "Upload URL has expired — request a fresh one");
    }
}
