package com.dreamhomes.haven.photo.storage;

import org.springframework.web.multipart.MultipartFile;

/**
 * Strategy interface for getting an uploaded image into hostable storage and
 * returning the public URL the rest of the app records on the {@code ListingPhoto}
 * row.
 *
 * <p>Implementations are wired by {@code @ConditionalOnProperty} on
 * {@code haven.photos.storage}:
 * <ul>
 *   <li>{@code r2} — {@link R2PhotoStorage}. Production / staging path; uploads to
 *       Cloudflare R2 via the AWS SDK v2 S3 client.</li>
 *   <li>{@code local} (default) — {@link LocalPhotoStorage}. Dev / test path that
 *       returns a deterministic URL without actually uploading anywhere — keeps the
 *       contract intact for tests + local dev without needing R2 credentials.</li>
 * </ul>
 */
public interface PhotoStorage {

    /**
     * Upload {@code file} for the given {@code listingId}. Returns the public URL the
     * file is now hostable at. Implementations decide the key shape under the hood;
     * callers only see the URL.
     *
     * @throws PhotoUploadException if the file is missing, has no content type, or
     *         the upload to the underlying store fails.
     */
    String upload(MultipartFile file, Long listingId);
}
