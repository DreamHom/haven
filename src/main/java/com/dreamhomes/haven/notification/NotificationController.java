package com.dreamhomes.haven.notification;

import com.dreamhomes.haven.auth.JwtPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Notifications")
public class NotificationController {

    private static final int MAX_PAGE_SIZE = 100;

    private final NotificationService notificationService;
    private final NotificationSseEmitters sseEmitters;

    @io.swagger.v3.oas.annotations.Operation(
            summary = "Open a server-sent-events stream of my notifications",
            description = """
                    Opens a long-lived `text/event-stream` connection. Every notification
                    written for the caller — synchronous or Kafka-driven — is pushed as an
                    SSE event the moment it commits. The event `name` is the
                    {@code NotificationKind}; the {@code data} payload is
                    `{ "id": ..., "kind": ..., "payload": {...} }`.

                    Persona audit (Temi, Ngozi): the previous "poll /notifications/mine
                    every 10s" pattern was both wasteful and laggy. SSE collapses both:
                    no wasted hits when there's nothing new, and the user sees the event
                    within milliseconds of it being recorded.

                    Single-instance only for now — a future Redis pub-sub layer would
                    fan out across nodes. Clients should reconnect on disconnect (browsers'
                    EventSource does this automatically with backoff).
                    """
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "SSE stream opened; events arrive as notifications are recorded."),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                    ref = "#/components/responses/Unauthenticated")
    })
    @io.swagger.v3.oas.annotations.security.SecurityRequirement(name = "bearerAuth")
    @GetMapping(value = "/stream",
            produces = org.springframework.http.MediaType.TEXT_EVENT_STREAM_VALUE)
    public org.springframework.web.servlet.mvc.method.annotation.SseEmitter stream(
            @org.springframework.security.core.annotation.AuthenticationPrincipal
            com.dreamhomes.haven.auth.JwtPrincipal principal) {
        return sseEmitters.register(principal.userId());
    }

    @Operation(
            summary = "List my notifications",
            description = """
                    Paginated inbox scoped to the calling user. Notifications come from \
                    multiple sources:

                    - **Sync writes**: verification approvals/rejections, agent-assignment \
                      handshake decisions, listing takedowns, etc. — written in the same \
                      transaction as the action that triggered them.
                    - **Async Kafka events**: inspection requests, offer submissions — \
                      published via the transactional outbox, consumed by listeners that \
                      write the notification with `event_id` dedup.

                    Use `?unreadOnly=true` to get just the unread inbox.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200",
                    description = "Paginated notifications.",
                    content = @Content(
                            examples = @ExampleObject(name = "TwoNotifications", value = """
                                    { "content": [
                                        { "id": 201, "kind": "OFFER_SUBMITTED",
                                          "source": "ASYNC", "recipientUserId": 7,
                                          "body": "An applicant submitted an offer of NGN 7,500,000 on your listing.",
                                          "data": { "listingId": 17, "offerId": 42, "amount": 7500000 },
                                          "readAt": null, "createdAt": "2026-05-10T08:30:01Z" },
                                        { "id": 200, "kind": "VERIFICATION_APPROVED",
                                          "source": "SYNC", "recipientUserId": 7,
                                          "body": "Your owner-identity verification was approved.",
                                          "data": { "verificationId": 99, "type": "OWNER_IDENTITY" },
                                          "readAt": "2026-05-10T07:55:00Z",
                                          "createdAt": "2026-05-10T07:50:00Z" }
                                      ],
                                      "page": { "size": 20, "number": 0, "totalElements": 2, "totalPages": 1 } }
                                    """))),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthenticated")
    })
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/mine")
    public Page<NotificationResponse> listMine(
            @AuthenticationPrincipal JwtPrincipal principal,
            @Parameter(description = "When true, return only notifications without `readAt`.", example = "false")
            @RequestParam(defaultValue = "false") boolean unreadOnly,
            @Parameter(description = "Filter by notification kind (OFFER_SUBMITTED, VERIFICATION_APPROVED, etc).")
            @RequestParam(required = false) com.dreamhomes.haven.notification.model.NotificationKind kind,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE));
        return notificationService.listMine(principal.userId(), unreadOnly, kind, pageable)
                .map(NotificationResponse::from);
    }

    @Operation(
            summary = "Get the count of my unread notifications",
            description = """
                    Cheap COUNT query over the calling user's notifications where \
                    `readAt IS NULL`.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200",
                    description = "JSON object with the unread count.",
                    content = @Content(
                            examples = @ExampleObject(name = "ThreeUnread", value = """
                                    { "unread": 3 }
                                    """))),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthenticated")
    })
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/mine/unread-count")
    public Map<String, Long> countUnread(@AuthenticationPrincipal JwtPrincipal principal) {
        return Map.of("unread", notificationService.countUnread(principal.userId()));
    }

    @Operation(
            summary = "Mark a notification as read",
            description = """
                    Stamps `readAt` on the target notification (idempotent — already-read \
                    re-mark is a no-op). The notification must belong to the caller; \
                    marking someone else's notification as read returns 403.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200",
                    description = "Updated notification with `readAt` populated.",
                    content = @Content(schema = @Schema(implementation = NotificationResponse.class))),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthenticated"),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/Forbidden"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/NotFound")
    })
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/{id}/mark-read")
    public NotificationResponse markRead(
            @AuthenticationPrincipal JwtPrincipal principal,
            @Parameter(description = "Notification ID to mark read.", example = "201")
            @PathVariable Long id) {
        return NotificationResponse.from(notificationService.markRead(principal.userId(), id));
    }

    @Operation(
            summary = "Mark all my notifications as read",
            description = """
                    Bulk-flip every unread notification for the caller to read. Idempotent —
                    already-read notifications are skipped. Returns the count actually flipped.
                    Persona audit (Biodun, Temi) flagged that one-at-a-time mark-read is
                    unusable past a handful of notifications.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200",
                    description = "Returns `{ \"marked\": <int> }` — count of notifications flipped.",
                    content = @Content(examples = @ExampleObject(name = "MarkedFive", value = """
                            { "marked": 5 }
                            """))),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/Unauthenticated")
    })
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/mark-all-read")
    public Map<String, Integer> markAllRead(@AuthenticationPrincipal JwtPrincipal principal) {
        return Map.of("marked", notificationService.markAllRead(principal.userId()));
    }
}
