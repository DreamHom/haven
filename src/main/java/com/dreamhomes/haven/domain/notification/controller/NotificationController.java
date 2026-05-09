package com.dreamhomes.haven.domain.notification.controller;

import com.dreamhomes.haven.domain.notification.dto.NotificationResponse;
import com.dreamhomes.haven.domain.notification.service.NotificationService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public List<NotificationResponse> list(@RequestParam Long userId) {
        return notificationService.listForUser(userId).stream()
                .map(n -> new NotificationResponse(n.getId(), n.getType(), n.getPayload(), n.getCreatedAt()))
                .toList();
    }
}