package com.dreamhomes.haven.platform;

import com.dreamhomes.haven.platform.model.PlatformSettings;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlatformSettingsRepository extends JpaRepository<PlatformSettings, Short> {
}
