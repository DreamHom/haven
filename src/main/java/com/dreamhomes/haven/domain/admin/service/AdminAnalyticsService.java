package com.dreamhomes.haven.domain.admin.service;

import com.dreamhomes.haven.domain.admin.dto.AnalyticsSummaryResponse;
import org.springframework.stereotype.Service;

@Service
public class AdminAnalyticsService {
    public AnalyticsSummaryResponse summary() {
        return new AnalyticsSummaryResponse(0, 0, 0, 0, 0);
    }
}

