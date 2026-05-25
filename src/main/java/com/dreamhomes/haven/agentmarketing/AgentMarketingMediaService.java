package com.dreamhomes.haven.agentmarketing;

import com.dreamhomes.haven.agentmarketing.dto.AgentMarketingMediaResponse;
import com.dreamhomes.haven.agentmarketing.exception.AgentMarketingInvalidImageException;
import com.dreamhomes.haven.agentmarketing.exception.AgentMarketingInvalidOrderException;
import com.dreamhomes.haven.agentmarketing.exception.AgentMarketingMediaNotFoundException;
import com.dreamhomes.haven.agentmarketing.exception.AgentMarketingQuotaExceededException;
import com.dreamhomes.haven.agentmarketing.exception.NotYourMarketingMediaException;
import com.dreamhomes.haven.agentmarketing.model.AgentMarketingMedia;
import com.dreamhomes.haven.common.config.CacheConfig;
import com.dreamhomes.haven.photo.storage.AgentMarketingPhotoStorage;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AgentMarketingMediaService {

    static final int MAX_ITEMS = 24;
    static final int MAX_CAPTION_LEN = 512;

    private static final Set<String> ALLOWED_IMAGE_TYPES = Set.of(
            "image/jpeg",
            "image/jpg",
            "image/png",
            "image/webp",
            "image/gif");

    private final AgentMarketingMediaRepository repository;
    private final AgentMarketingPhotoStorage photoStorage;

    @Value("${haven.photos.agent-marketing.max-bytes:8388608}")
    private long agentMarketingMaxBytes;

    @Transactional(readOnly = true)
    public List<AgentMarketingMediaResponse> listMine(Long userId) {
        return repository.findByUserIdOrderByDisplayOrderAscIdAsc(userId).stream()
                .map(AgentMarketingMediaService::toResponse)
                .toList();
    }

    @Transactional
    // The agent's public profile embeds the gallery; uploading invalidates the cached
    // projection so the next /api/users/{id}/profile read includes the new item.
    @CacheEvict(value = CacheConfig.USERS_PUBLIC_PROFILE, key = "#userId")
    public AgentMarketingMediaResponse upload(Long userId, MultipartFile file, String caption) {
        validateMarketingImage(file);
        if (repository.countByUserId(userId) >= MAX_ITEMS) {
            throw new AgentMarketingQuotaExceededException(MAX_ITEMS);
        }
        String url = photoStorage.upload(file, userId);
        int order = (int) repository.countByUserId(userId);
        String safeCaption = normalizeCaption(caption);
        AgentMarketingMedia row = repository.save(AgentMarketingMedia.builder()
                .userId(userId)
                .url(url)
                .caption(safeCaption)
                .displayOrder(order)
                .uploadedAt(Instant.now())
                .build());
        return toResponse(row);
    }

    @Transactional
    // Public profile cache embeds the gallery — flush so the deleted media disappears
    // from the next /api/users/{id}/profile read.
    @CacheEvict(value = CacheConfig.USERS_PUBLIC_PROFILE, key = "#userId")
    public void deleteMine(Long userId, Long mediaId) {
        AgentMarketingMedia row = repository.findById(mediaId)
                .orElseThrow(() -> new AgentMarketingMediaNotFoundException(mediaId));
        if (!row.getUserId().equals(userId)) {
            throw new NotYourMarketingMediaException();
        }
        repository.delete(row);
    }

    @Transactional
    // Display order on the public profile reflects gallery order — flush.
    @CacheEvict(value = CacheConfig.USERS_PUBLIC_PROFILE, key = "#userId")
    public void reorderMine(Long userId, List<Long> mediaIdsInOrder) {
        List<AgentMarketingMedia> rows = repository.findByUserIdOrderByDisplayOrderAscIdAsc(userId);
        if (rows.isEmpty()) {
            throw new AgentMarketingInvalidOrderException("Gallery is empty");
        }
        if (mediaIdsInOrder.size() != rows.size()) {
            throw new AgentMarketingInvalidOrderException("Must list every gallery item exactly once");
        }
        Set<Long> expected = rows.stream().map(AgentMarketingMedia::getId).collect(Collectors.toSet());
        Set<Long> seen = new HashSet<>();
        for (Long id : mediaIdsInOrder) {
            if (id == null || !expected.contains(id) || !seen.add(id)) {
                throw new AgentMarketingInvalidOrderException("Ids must match your gallery items with no duplicates");
            }
        }
        Map<Long, AgentMarketingMedia> byId = rows.stream()
                .collect(Collectors.toMap(AgentMarketingMedia::getId, Function.identity()));
        for (int i = 0; i < mediaIdsInOrder.size(); i++) {
            byId.get(mediaIdsInOrder.get(i)).setDisplayOrder(i);
        }
        repository.saveAll(rows);
    }

    private void validateMarketingImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new AgentMarketingInvalidImageException("Uploaded file is empty");
        }
        if (file.getSize() > agentMarketingMaxBytes) {
            throw new AgentMarketingInvalidImageException("Image exceeds maximum allowed size");
        }
        String ct = file.getContentType();
        if (ct == null || ct.isBlank()) {
            throw new AgentMarketingInvalidImageException("Content-Type is required for gallery uploads");
        }
        String norm = ct.toLowerCase(Locale.ROOT).trim();
        if (!ALLOWED_IMAGE_TYPES.contains(norm)) {
            throw new AgentMarketingInvalidImageException("Only common web image types are allowed (JPEG, PNG, WebP, GIF)");
        }
    }

    private static String normalizeCaption(String caption) {
        if (caption == null) {
            return null;
        }
        String t = caption.trim();
        if (t.isEmpty()) {
            return null;
        }
        return t.length() > MAX_CAPTION_LEN ? t.substring(0, MAX_CAPTION_LEN) : t;
    }

    private static AgentMarketingMediaResponse toResponse(AgentMarketingMedia m) {
        return new AgentMarketingMediaResponse(m.getId(), m.getUrl(), m.getCaption(), m.getDisplayOrder(), m.getUploadedAt());
    }
}
