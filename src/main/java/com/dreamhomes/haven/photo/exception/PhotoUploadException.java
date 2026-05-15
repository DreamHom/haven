package com.dreamhomes.haven.photo.exception;

import com.dreamhomes.haven.common.DomainException;
import org.springframework.http.HttpStatus;

/**
 * Thrown when the photo upload pipeline cannot accept the file — empty multipart,
 * unsupported content type, R2 error talking to the bucket, etc. Maps to 400 because
 * "we couldn't accept your upload" is a client-side problem in the common cases
 * (empty file, wrong content-type) and a 5xx-shaped problem in the storage-down case
 * we can't recover from on the request thread either way.
 */
public class PhotoUploadException extends DomainException {

    public PhotoUploadException(String message) {
        super(HttpStatus.BAD_REQUEST, message);
    }

    public PhotoUploadException(String message, Throwable cause) {
        super(HttpStatus.BAD_REQUEST, message);
        initCause(cause);
    }
}
