package com.dreamhomes.haven.photo.storage;

import java.net.URI;
import java.time.Duration;
import java.util.Optional;

/**
 * Browser-direct upload backend (Item 2). Companion of {@link PhotoStorage}, which
 * handles the legacy multipart-proxy path.
 *
 * <p>Two operations:
 * <ul>
 *   <li>{@link #presignUpload(String, String, long, Duration)} — mint a single-use,
 *       short-TTL HTTPS URL the browser can {@code PUT} bytes to without touching
 *       Haven.</li>
 *   <li>{@link #headObject(String)} — verify the upload landed (object exists) and
 *       discover its actual size. Confirm uses this to reject mismatched / missing
 *       uploads with 422.</li>
 * </ul>
 *
 * <p>Implementations are wired by {@code @ConditionalOnProperty} on
 * {@code haven.photos.storage} — same selector as {@link PhotoStorage}:
 * <ul>
 *   <li>{@code r2} → {@code R2PresignedPhotoStorage} (real S3 presigner + HEAD)</li>
 *   <li>{@code local} (default) → {@code LocalPresignedPhotoStorage} (synthesised URL
 *       + simulated HEAD that always succeeds with a configurable size; keeps tests
 *       free of R2 credentials)</li>
 * </ul>
 */
public interface PhotoPresignedStorage {

    /**
     * Mint a pre-signed PUT URL for a single upload.
     *
     * @param fileKey       object key (incl. listings/{id}/{uuid}.{ext} layout)
     * @param contentType   MIME type the URL is bound to (R2 verifies on PUT)
     * @param maxSizeBytes  byte ceiling the URL is bound to
     * @param ttl           how long the URL stays valid
     * @return the URL the browser will PUT to
     */
    URI presignUpload(String fileKey, String contentType, long maxSizeBytes, Duration ttl);

    /**
     * HEAD the object to verify it landed. Returned {@link HeadResult#exists()} is
     * false when the object isn't there; {@link HeadResult#sizeBytes()} reports the
     * actual bytes when it is.
     */
    HeadResult headObject(String fileKey);

    /** Public URL the rest of the app records on the {@code ListingPhoto} row. */
    String publicUrlFor(String fileKey);

    /** Result of {@link #headObject(String)} — exists + size when {@code exists()} is true. */
    record HeadResult(boolean exists, Long sizeBytes, Optional<String> contentType) {
        public static HeadResult missing() {
            return new HeadResult(false, null, Optional.empty());
        }
        public static HeadResult of(long sizeBytes, String contentType) {
            return new HeadResult(true, sizeBytes, Optional.ofNullable(contentType));
        }
    }
}
