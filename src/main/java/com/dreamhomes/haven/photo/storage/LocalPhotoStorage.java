package com.dreamhomes.haven.photo.storage;

import com.dreamhomes.haven.photo.exception.PhotoUploadException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

/**
 * Default {@link PhotoStorage} for dev + test. Does not actually persist anything;
 * returns a deterministic-looking URL so callers get back a usable response and the
 * row written to {@code listing_photos} has a real-looking value.
 *
 * <p>Useful because:
 * <ul>
 *   <li>ITs can exercise the entire upload pipeline (multipart in → URL out → row
 *       written) without needing R2 credentials.</li>
 *   <li>Local dev can publish photos and see them flow through the API surface
 *       without setting up a bucket.</li>
 * </ul>
 *
 * <p>The URL it returns points at {@code media.dreamhomes.com} — that domain is
 * not actually serving anything, so the URL is a placeholder, not a working asset.
 * Production must override {@code haven.photos.storage=r2} to switch to
 * {@link R2PhotoStorage}.</p>
 */
@Component
@ConditionalOnProperty(value = "haven.photos.storage", havingValue = "local", matchIfMissing = true)
@Slf4j
public class LocalPhotoStorage implements PhotoStorage {

    @Override
    public String upload(MultipartFile file, Long listingId) {
        if (file == null || file.isEmpty()) {
            throw new PhotoUploadException("uploaded file is empty");
        }
        String extension = extensionOf(file);
        String key = "listings/" + listingId + "/" + UUID.randomUUID() + extension;
        String url = "https://media.dreamhomes.com/" + key;
        log.info("LocalPhotoStorage: synthesised URL for listing {} (no bytes persisted): {}",
                listingId, url);
        return url;
    }

    private static String extensionOf(MultipartFile file) {
        String original = file.getOriginalFilename();
        if (original == null) return "";
        int dot = original.lastIndexOf('.');
        return dot < 0 ? "" : original.substring(dot).toLowerCase();
    }
}
