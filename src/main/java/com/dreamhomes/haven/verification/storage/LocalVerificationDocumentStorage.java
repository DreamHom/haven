package com.dreamhomes.haven.verification.storage;

import com.dreamhomes.haven.photo.exception.PhotoUploadException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

/**
 * Dev / test {@link VerificationDocumentStorage} — synthesises a deterministic URL
 * without persisting bytes. Activated by {@code haven.photos.storage=local} (we
 * piggy-back on the same toggle as photos for consistency).
 */
@Component
@ConditionalOnProperty(value = "haven.photos.storage", havingValue = "local", matchIfMissing = true)
@Slf4j
public class LocalVerificationDocumentStorage implements VerificationDocumentStorage {

    @Override
    public String upload(MultipartFile file, Long userId) {
        if (file == null || file.isEmpty()) {
            throw new PhotoUploadException("uploaded file is empty");
        }
        String key = "verifications/" + userId + "/" + UUID.randomUUID() + extensionOf(file);
        String url = "https://media.dreamhomes.com/" + key;
        log.info("LocalVerificationDocumentStorage: synthesised URL for user {}: {}", userId, url);
        return url;
    }

    private static String extensionOf(MultipartFile file) {
        String original = file.getOriginalFilename();
        if (original == null) return "";
        int dot = original.lastIndexOf('.');
        return dot < 0 ? "" : original.substring(dot).toLowerCase();
    }
}
