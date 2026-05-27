package com.dreamhomes.haven.photo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Item 2 — server-side reservation for an upcoming browser-direct R2 upload.
 *
 * <p>Lifecycle:
 * <ol>
 *   <li>Client calls {@code POST /api/listings/{id}/photos/upload-url}. Server mints an
 *       R2 pre-signed PUT URL via {@code S3Presigner}, persists a row here with
 *       {@code confirmedAt=null}, and returns the URL + 10-min expiry to the browser.</li>
 *   <li>Browser PUTs the bytes to R2 directly (Haven's bandwidth uninvolved).</li>
 *   <li>Client calls {@code POST /api/listings/{id}/photos/confirm} with the same
 *       {@code fileKey}. Server HEADs R2 to verify the object exists + size matches,
 *       writes a {@code listing_photos} row, stamps {@code confirmedAt} on this row,
 *       and returns the photo response.</li>
 * </ol>
 *
 * <p>An hourly {@code @Scheduled} job purges rows older than 24h, confirmed or not —
 * confirmed rows have done their job; unconfirmed rows past expiry are orphans the
 * caller never came back for.
 */
@Entity
@Table(name = "photo_upload_intent")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PhotoUploadIntent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "listing_id", nullable = false)
    private Long listingId;

    @Column(name = "requested_by", nullable = false)
    private Long requestedBy;

    /** R2 object key. Unique across all intents (V46 UQ). */
    @Column(name = "file_key", nullable = false, length = 512)
    private String fileKey;

    @Column(name = "content_type", nullable = false, length = 64)
    private String contentType;

    /** Max bytes the pre-signed URL allows. Confirm rejects with 422 if the actual size exceeds this. */
    @Column(name = "max_size_bytes", nullable = false)
    private Long maxSizeBytes;

    /** Pre-signed URL expiry; confirm beyond this point is 409 (expired). */
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    /** Null until /confirm succeeds; non-null guards against double-confirm (409). */
    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    /** The id of the {@code ListingPhoto} row created when confirm succeeded; null otherwise. */
    @Column(name = "confirmed_photo_id")
    private Long confirmedPhotoId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
