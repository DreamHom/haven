package com.dreamhomes.haven.photo.storage;

import org.springframework.web.multipart.MultipartFile;

/**
 * Persists agent marketing gallery images (R2 or local stub), keyed under {@code agents/{userId}/…}.
 */
public interface AgentMarketingPhotoStorage {

    String upload(MultipartFile file, Long userId);
}
