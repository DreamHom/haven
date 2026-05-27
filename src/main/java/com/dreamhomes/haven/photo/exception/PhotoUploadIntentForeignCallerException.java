package com.dreamhomes.haven.photo.exception;

import com.dreamhomes.haven.common.DomainException;
import org.springframework.http.HttpStatus;

/**
 * Item 2 — caller's userId doesn't match the {@code requested_by} on the intent row
 * they're trying to confirm, OR the intent is for a different listing than the path
 * variable. 403 because the auth check is the right signal: this caller cannot
 * confirm someone else's intent.
 */
public class PhotoUploadIntentForeignCallerException extends DomainException {

    public PhotoUploadIntentForeignCallerException() {
        super(HttpStatus.FORBIDDEN,
                "Upload intent doesn't belong to you");
    }
}
