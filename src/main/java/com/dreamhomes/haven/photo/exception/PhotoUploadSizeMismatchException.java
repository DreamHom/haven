package com.dreamhomes.haven.photo.exception;

import com.dreamhomes.haven.common.DomainException;
import org.springframework.http.HttpStatus;

/**
 * Item 2 — caller claimed one size on /confirm but R2 HEAD reports a different one.
 * 422: usually means a partial upload or a swap-trick. Surface loudly rather than
 * silently recording a row that doesn't match the bytes.
 */
public class PhotoUploadSizeMismatchException extends DomainException {

    public PhotoUploadSizeMismatchException(long claimedBytes, long actualBytes) {
        super(HttpStatus.UNPROCESSABLE_ENTITY,
                "Size mismatch: claimed " + claimedBytes
                        + " bytes, actual " + actualBytes + " bytes");
    }
}
