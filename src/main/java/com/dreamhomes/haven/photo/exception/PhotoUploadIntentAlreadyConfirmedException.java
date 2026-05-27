package com.dreamhomes.haven.photo.exception;

import com.dreamhomes.haven.common.DomainException;
import org.springframework.http.HttpStatus;

/**
 * Item 2 — confirm called twice on the same {@code fileKey}. 409: idempotency would
 * silently overwrite the first photo row, which is worse than telling the caller
 * loudly that they've already used this intent.
 */
public class PhotoUploadIntentAlreadyConfirmedException extends DomainException {

    public PhotoUploadIntentAlreadyConfirmedException() {
        super(HttpStatus.CONFLICT,
                "This upload intent was already confirmed — request a fresh URL");
    }
}
