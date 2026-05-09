package com.dreamhomes.haven.domain.admin.controller;

import com.dreamhomes.haven.domain.admin.dto.AnalyticsSummaryResponse;
import com.dreamhomes.haven.domain.admin.service.AdminAnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/analytics")
@RequiredArgsConstructor
public class AdminAnalyticsController {

    private final AdminAnalyticsService analyticsService;

    @GetMapping("/summary")
    public AnalyticsSummaryResponse summary() {
        return analyticsService.summary();
    }
}

