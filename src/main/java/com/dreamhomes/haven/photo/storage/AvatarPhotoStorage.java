package com.dreamhomes.haven.photo.storage;

import org.springframework.web.multipart.MultipartFile;

/**
 * Persists a user's profile avatar to the configured object store (R2 or local stub).
 */
public interface AvatarPhotoStorage {

    /**
     * @param userId owner segment in the object key namespace
     * @return public URL clients may store on {@code users.profile_image_url}
     */
    String upload(MultipartFile file, Long userId);
}
