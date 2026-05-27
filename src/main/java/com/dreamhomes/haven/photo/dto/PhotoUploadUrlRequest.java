package com.dreamhomes.haven.photo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Item 2 — request body for {@code POST /api/listings/{id}/photos/upload-url}. The
 * server uses the supplied {@code contentType} and {@code sizeBytes} to bind the
 * pre-signed PUT URL it mints; the browser MUST send these same values when it PUTs
 * to R2 or the bucket refuses the upload.
 *
 * <p>{@code originalFilename} is optional — purely used to slug the object key so the
 * stored URL keeps the user's filename hint (e.g. {@code ...abc-uuid-living-room.jpg}).
 * The file extension is derived from {@code contentType}, not from this field.</p>
 */
public record PhotoUploadUrlRequest(
        @Schema(description = "MIME type. Must be image/jpeg, image/png, or image/webp.",
                example = "image/jpeg")
        @NotBlank
        String contentType,

        @Schema(description = "Expected upload size in bytes. Cap is 10 MB.",
                example = "523456")
        @Positive
        long sizeBytes,

        @Schema(description = "Optional source filename; used for slug in the object key.",
                example = "living-room.jpg")
        @Size(max = 255)
        String originalFilename
) {
}
