package com.dreamhomes.haven.photo.storage;

import com.dreamhomes.haven.photo.exception.PhotoUploadException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@Component
@ConditionalOnProperty(value = "haven.photos.storage", havingValue = "local", matchIfMissing = true)
@Slf4j
public class LocalAvatarPhotoStorage implements AvatarPhotoStorage {

    @Override
    public String upload(MultipartFile file, Long userId) {
        if (file == null || file.isEmpty()) {
            throw new PhotoUploadException("uploaded file is empty");
        }
        String extension = extensionOf(file);
        String key = "avatars/" + userId + "/" + UUID.randomUUID() + extension;
        String url = "https://media.dreamhomes.com/" + key;
        log.info("LocalAvatarPhotoStorage: synthesised URL for userId={} (no bytes persisted): {}",
                userId, url);
        return url;
    }

    private static String extensionOf(MultipartFile file) {
        String original = file.getOriginalFilename();
        if (original == null) {
            return "";
        }
        int dot = original.lastIndexOf('.');
        return dot < 0 ? "" : original.substring(dot).toLowerCase();
    }
}
