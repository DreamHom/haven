package com.dreamhomes.haven.platform;

import com.dreamhomes.haven.platform.dto.PatchPlatformSettingsRequest;
import com.dreamhomes.haven.platform.dto.PlatformSettingsResponse;
import com.dreamhomes.haven.platform.model.PlatformSettings;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PlatformSettingsService {

    private final PlatformSettingsRepository platformSettingsRepository;

    @Transactional(readOnly = true)
    public PlatformSettingsResponse get() {
        PlatformSettings row = loadSingleton();
        return toResponse(row);
    }

    @Transactional
    public PlatformSettingsResponse merge(PatchPlatformSettingsRequest request) {
        PlatformSettings row = loadSingleton();
        Map<String, Object> merged = new LinkedHashMap<>(row.getSettings() != null ? row.getSettings() : Map.of());
        merged.putAll(request.patch());
        row.setSettings(merged);
        return toResponse(platformSettingsRepository.save(row));
    }

    private PlatformSettings loadSingleton() {
        return platformSettingsRepository.findById(PlatformSettings.SINGLETON_ID)
                .orElseGet(() -> platformSettingsRepository.save(PlatformSettings.builder()
                        .id(PlatformSettings.SINGLETON_ID)
                        .settings(new LinkedHashMap<>())
                        .build()));
    }

    private static PlatformSettingsResponse toResponse(PlatformSettings row) {
        return new PlatformSettingsResponse(
                row.getSettings() != null ? Map.copyOf(row.getSettings()) : Map.of(),
                row.getUpdatedAt());
    }
}
