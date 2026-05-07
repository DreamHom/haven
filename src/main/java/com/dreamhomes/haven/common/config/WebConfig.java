package com.dreamhomes.haven.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.web.config.EnableSpringDataWebSupport;
import org.springframework.data.web.config.EnableSpringDataWebSupport.PageSerializationMode;

/**
 * Stabilises {@code Page} JSON output. Spring Boot 3.3 warns that serialising
 * {@code PageImpl} directly is shape-unstable across versions; switching to
 * {@code VIA_DTO} pins the JSON shape so frontend integration doesn't drift.
 */
@Configuration
@EnableSpringDataWebSupport(pageSerializationMode = PageSerializationMode.VIA_DTO)
public class WebConfig {
}
