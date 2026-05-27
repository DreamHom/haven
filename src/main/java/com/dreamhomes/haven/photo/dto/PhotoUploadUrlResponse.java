package com.dreamhomes.haven.photo.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

/**
 * Item 2 — response from {@code POST /api/listings/{id}/photos/upload-url}. The browser
 * PUTs raw bytes directly to {@code uploadUrl}, then calls {@code /confirm} with the
 * {@code fileKey} to register the photo. The {@code expiresAt} timestamp is when the
 * pre-signed URL stops working — after that the browser must request a fresh one.
 */
public record PhotoUploadUrlResponse(
        @Schema(description = "Pre-signed HTTPS URL the browser should PUT raw image bytes to.",
                example = "https://media.dreamhomes.com/listings/17/abc-uuid.jpg?X-Amz-Algorithm=...")
        String uploadUrl,

        @Schema(description = "R2 object key the URL targets. Pass it back to /confirm verbatim.",
                example = "listings/17/abc-uuid-hero.jpg")
        String fileKey,

        @Schema(description = "URL expiry (Instant). Confirm beyond this returns 409.",
                example = "2026-05-24T10:00:00Z")
        Instant expiresAt,

        @Schema(description = "Max bytes the URL accepts.", example = "10485760")
        long maxSizeBytes,

        @Schema(description = "Content types the URL accepts.",
                example = "[\"image/jpeg\", \"image/png\", \"image/webp\"]")
        List<String> allowedContentTypes
) {
}
