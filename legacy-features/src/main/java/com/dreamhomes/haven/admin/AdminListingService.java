package com.dreamhomes.haven.admin;

import com.dreamhomes.haven.listing.Listing;
import com.dreamhomes.haven.listing.ListingNotFoundException;
import com.dreamhomes.haven.listing.ListingRepository;
import com.dreamhomes.haven.listing.ListingStatus;
import com.dreamhomes.haven.notification.Notification;
import com.dreamhomes.haven.notification.NotificationKind;
import com.dreamhomes.haven.notification.NotificationRepository;
import com.dreamhomes.haven.notification.NotificationSource;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Admin-only listing actions:
 * <ul>
 *   <li><b>approve</b> — grants the verified-listing badge ({@code listings.approved_at}).
 *       Per PRD §4.1 listings are LIVE on creation regardless; approval is a non-blocking
 *       trust signal, not a visibility gate.</li>
 *   <li><b>takedown</b> — admin-driven transition to {@code CLOSED}. Bypasses the
 *       owner-driven transition rules in {@code ListingService} on purpose; admins are
 *       the platform's safety net per PRD §4.10.</li>
 * </ul>
 *
 * <p>Both actions write an {@link AdminAuditLog} row and a sync {@link Notification}
 * to the listing owner (PRD §7: listing approvals are sync DB notifications, not Kafka).
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AdminListingService {

    private final ListingRepository listingRepository;
    private final NotificationRepository notificationRepository;
    private final AdminAuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;
    private final AdminMetrics adminMetrics;

    @Transactional
    public Listing approve(Long adminId, Long listingId) {
        Listing listing = listingRepository.findById(listingId)
                .orElseThrow(() -> new ListingNotFoundException(listingId));
        if (listing.getApprovedAt() != null) {
            throw new ListingAlreadyApprovedException(listingId);
        }
        Instant now = Instant.now();
        listing.setApprovedAt(now);
        listing.setUpdatedAt(now);
        listingRepository.save(listing);

        recordAudit(adminId, AdminAction.LISTING_APPROVED, listing, null);
        recordOwnerNotification(listing, NotificationKind.LISTING_APPROVED, null);
        adminMetrics.recordListingAction(AdminAction.LISTING_APPROVED);

        log.info("Admin {} approved listingId={}", adminId, listingId);
        return listing;
    }

    @Transactional
    public Listing takedown(Long adminId, Long listingId, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Takedown reason is required");
        }
        Listing listing = listingRepository.findById(listingId)
                .orElseThrow(() -> new ListingNotFoundException(listingId));
        if (listing.getStatus() == ListingStatus.CLOSED) {
            throw new ListingAlreadyClosedException(listingId);
        }
        Instant now = Instant.now();
        listing.setStatus(ListingStatus.CLOSED);
        listing.setUpdatedAt(now);
        listingRepository.save(listing);

        recordAudit(adminId, AdminAction.LISTING_TAKEDOWN, listing, reason);
        recordOwnerNotification(listing, NotificationKind.LISTING_TAKEDOWN, reason);
        adminMetrics.recordListingAction(AdminAction.LISTING_TAKEDOWN);

        log.info("Admin {} took down listingId={} reason='{}'", adminId, listingId, reason);
        return listing;
    }

    private void recordAudit(Long adminId, AdminAction action, Listing listing, String reason) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("ownerId", listing.getOwnerId());
        metadata.put("status", listing.getStatus().name());
        if (reason != null && !reason.isBlank()) {
            metadata.put("reason", reason);
        }
        auditLogRepository.save(AdminAuditLog.builder()
                .adminId(adminId)
                .action(action)
                .targetType(AuditTargetType.LISTING)
                .targetId(listing.getId())
                .metadata(serialize(metadata))
                .createdAt(Instant.now())
                .build());
    }

    private void recordOwnerNotification(Listing listing, NotificationKind kind, String reason) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("listingId", listing.getId());
        payload.put("status", listing.getStatus().name());
        if (reason != null && !reason.isBlank()) {
            payload.put("reason", reason);
        }
        notificationRepository.save(Notification.builder()
                .recipientId(listing.getOwnerId())
                .kind(kind)
                .source(NotificationSource.SYNC)
                .payload(serialize(payload))
                .createdAt(Instant.now())
                .build());
    }

    private String serialize(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialise admin listing payload", e);
        }
    }
}
