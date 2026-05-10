package com.dreamhomes.haven.notification;

import com.dreamhomes.haven.auth.JwtPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Read-side notification endpoints. Every authenticated user — owner, agent, applicant,
 * admin — uses these to drain their inbox. Sync notifications written across the platform
 * (verification decisions, listing approvals, comments, agent-assignment handshakes) and
 * Kafka-driven ones (inspection requests, offer submissions) all flow through the same
 * three reads.
 */
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private static final int MAX_PAGE_SIZE = 100;

    private final NotificationService notificationService;

    @GetMapping("/mine")
    public Page<NotificationResponse> listMine(
            @AuthenticationPrincipal JwtPrincipal principal,
            @RequestParam(defaultValue = "false") boolean unreadOnly,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE));
        return notificationService.listMine(principal.userId(), unreadOnly, pageable)
                .map(NotificationResponse::from);
    }

    @GetMapping("/mine/unread-count")
    public Map<String, Long> countUnread(@AuthenticationPrincipal JwtPrincipal principal) {
        return Map.of("unread", notificationService.countUnread(principal.userId()));
    }

    @PostMapping("/{id}/mark-read")
    public NotificationResponse markRead(
            @AuthenticationPrincipal JwtPrincipal principal,
            @PathVariable Long id) {
        return NotificationResponse.from(notificationService.markRead(principal.userId(), id));
    }
}
