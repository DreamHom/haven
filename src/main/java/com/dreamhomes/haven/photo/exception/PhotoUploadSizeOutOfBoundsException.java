package com.dreamhomes.haven.photo.exception;

import com.dreamhomes.haven.common.DomainException;
import org.springframework.http.HttpStatus;

/**
 * Item 2 — caller supplied {@code sizeBytes} outside (0, 10 MiB]. 400 because the
 * input is invalid before we'd even mint a URL.
 */
public class PhotoUploadSizeOutOfBoundsException extends DomainException {

    public PhotoUploadSizeOutOfBoundsException(long sizeBytes, long maxSizeBytes) {
        super(HttpStatus.BAD_REQUEST,
                "sizeBytes " + sizeBytes + " is out of bounds (max " + maxSizeBytes + " bytes)");
    }
}
