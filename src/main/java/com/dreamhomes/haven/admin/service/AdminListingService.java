package com.dreamhomes.haven.admin.service;

import com.dreamhomes.haven.admin.dto.AdminListingLeadResponse;
import com.dreamhomes.haven.lead.ListingLeadRepository;
import com.dreamhomes.haven.lead.model.ListingLead;
import com.dreamhomes.haven.listing.ListingService;
import com.dreamhomes.haven.listing.exception.ListingNotFoundException;
import com.dreamhomes.haven.listing.ListingMapper;
import com.dreamhomes.haven.listing.dto.ListingResponse;
import com.dreamhomes.haven.listing.model.ListingStatus;
import com.dreamhomes.haven.notification.NotificationApi;
import com.dreamhomes.haven.notification.model.NotificationKind;
import com.dreamhomes.haven.admin.dto.ListingModerationSnapshotResponse;
import com.dreamhomes.haven.admin.dto.ListingTakedownAuditSnippet;
import com.dreamhomes.haven.photo.ListingPhotoRepository;
import com.dreamhomes.haven.property.PropertyService;
import com.dreamhomes.haven.property.dto.PropertyResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import com.dreamhomes.haven.admin.exception.ListingAlreadyApprovedException;
import com.dreamhomes.haven.admin.exception.ListingAlreadyClosedException;
import com.dreamhomes.haven.admin.model.AdminAction;
import com.dreamhomes.haven.admin.model.AdminAuditLog;
import com.dreamhomes.haven.admin.model.AuditTargetType;
import com.dreamhomes.haven.admin.AdminAuditLogRepository;
import com.dreamhomes.haven.admin.AdminMetrics;

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
 * <p>Listing mutations go through {@link ListingService}'s admin-write methods
 * ({@code markApproved}, {@code forceStatus}); audit and notification are sync
 * (PRD §7).
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AdminListingService {

    private final ListingService listingService;
    private final ListingMapper listingMapper;
    private final NotificationApi notificationApi;
    private final AdminAuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;
    private final AdminMetrics adminMetrics;
    private final PropertyService propertyService;
    private final ListingPhotoRepository listingPhotoRepository;
    private final ListingLeadRepository listingLeadRepository;

    @Transactional(readOnly = true)
    public Page<ListingResponse> catalog(ListingStatus status, Pageable pageable) {
        return listingService.adminCatalog(status, pageable).map(lwp ->
                listingMapper.toResponse(lwp.listing(), lwp.property(),
                        listingService.activeAgentUserId(lwp.listing().getId()),
                        listingService.pendingReportCount(lwp.listing().getId()),
                        lwp.ownerPublicBio()));
    }

    @Transactional(readOnly = true)
    public ListingModerationSnapshotResponse moderationSnapshot(Long listingId) {
        ListingResponse listing = listingService.findById(listingId);
        PropertyResponse property = propertyService.findById(listing.propertyId());
        long photoCount = listingPhotoRepository.countByListingId(listingId);
        ListingTakedownAuditSnippet snippet = auditLogRepository
                .findFirstByTargetTypeAndTargetIdAndActionOrderByCreatedAtDesc(
                        AuditTargetType.LISTING, listingId, AdminAction.LISTING_TAKEDOWN)
                .map(this::toTakedownSnippet)
                .orElse(null);
        return new ListingModerationSnapshotResponse(listing, property, photoCount, snippet);
    }

    @Transactional(readOnly = true)
    public Page<AdminListingLeadResponse> listingLeads(Long listingId, Pageable pageable) {
        if (!listingService.exists(listingId)) {
            throw new ListingNotFoundException(listingId);
        }
        return listingLeadRepository.findByListingIdOrderByCreatedAtDesc(listingId, pageable)
                .map(AdminListingService::toAdminLeadRow);
    }

    private static AdminListingLeadResponse toAdminLeadRow(ListingLead lead) {
        return new AdminListingLeadResponse(
                lead.getId(),
                lead.getListingId(),
                lead.getApplicantUserId(),
                lead.getMessage(),
                lead.getCreatedAt(),
                lead.getRevealedAt(),
                lead.getContactPhone(),
                lead.getContactEmail());
    }

    private ListingTakedownAuditSnippet toTakedownSnippet(AdminAuditLog log) {
        String reason = null;
        if (log.getMetadata() != null && !log.getMetadata().isBlank()) {
            try {
                Map<String, Object> map = objectMapper.readValue(log.getMetadata(), new TypeReference<>() { });
                Object r = map.get("reason");
                reason = r == null ? null : String.valueOf(r);
            } catch (JsonProcessingException ignored) {
                reason = null;
            }
        }
        return new ListingTakedownAuditSnippet(log.getId(), log.getAdminId(), log.getCreatedAt(), reason);
    }

    @Transactional
    public ListingResponse approve(Long adminId, Long listingId, String reason) {
        ListingResponse listing = listingService.findById(listingId);
        Instant now = Instant.now();

        // Two semantics for "approve":
        //   1. Re-publishing a previously-taken-down listing — flips status TAKEN_DOWN → LIVE.
        //   2. Stamping the verified-listing badge for the first time.
        // Both fall under the same endpoint; we branch on current state.
        if (listing.status() == ListingStatus.TAKEN_DOWN) {
            listingService.forceStatus(listingId, ListingStatus.LIVE, now);
            ListingResponse restored = listingService.findById(listingId);
            recordAudit(adminId, AdminAction.LISTING_APPROVED, restored, reason);
            recordOwnerNotification(restored, NotificationKind.LISTING_APPROVED, reason);
            adminMetrics.recordListingAction(AdminAction.LISTING_APPROVED);
            log.info("Admin {} re-published listingId={} (TAKEN_DOWN → LIVE) reason='{}'", adminId, listingId, reason);
            return restored;
        }
        if (listing.approvedAt() != null) {
            throw new ListingAlreadyApprovedException(listingId);
        }
        listingService.markApproved(listingId, now);

        recordAudit(adminId, AdminAction.LISTING_APPROVED, listing, reason);
        recordOwnerNotification(listing, NotificationKind.LISTING_APPROVED, reason);
        adminMetrics.recordListingAction(AdminAction.LISTING_APPROVED);

        log.info("Admin {} approved listingId={} reason='{}'", adminId, listingId, reason);
        return listingService.findById(listingId);
    }

    /** Back-compat overload — callers without a reason still work. */
    @Transactional
    public ListingResponse approve(Long adminId, Long listingId) {
        return approve(adminId, listingId, null);
    }

    @Transactional
    public ListingResponse takedown(Long adminId, Long listingId, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Takedown reason is required");
        }
        ListingResponse listing = listingService.findById(listingId);
        if (listing.status() == ListingStatus.CLOSED || listing.status() == ListingStatus.TAKEN_DOWN) {
            throw new ListingAlreadyClosedException(listingId);
        }
        Instant now = Instant.now();
        listingService.forceStatus(listingId, ListingStatus.TAKEN_DOWN, now);

        ListingResponse takenDown = listingService.findById(listingId);
        recordAudit(adminId, AdminAction.LISTING_TAKEDOWN, takenDown, reason);
        recordOwnerNotification(takenDown, NotificationKind.LISTING_TAKEDOWN, reason);
        adminMetrics.recordListingAction(AdminAction.LISTING_TAKEDOWN);

        log.info("Admin {} took down listingId={} reason='{}'", adminId, listingId, reason);
        return takenDown;
    }

    private void recordAudit(Long adminId, AdminAction action, ListingResponse listing, String reason) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("ownerId", listing.ownerId());
        metadata.put("status", listing.status().name());
        if (reason != null && !reason.isBlank()) {
            metadata.put("reason", reason);
        }
        auditLogRepository.save(AdminAuditLog.builder()
                .adminId(adminId)
                .action(action)
                .targetType(AuditTargetType.LISTING)
                .targetId(listing.id())
                .metadata(serialize(metadata))
                .build());
    }

    private void recordOwnerNotification(ListingResponse listing, NotificationKind kind, String reason) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("listingId", listing.id());
        payload.put("status", listing.status().name());
        if (reason != null && !reason.isBlank()) {
            payload.put("reason", reason);
        }
        notificationApi.recordSync(kind, listing.ownerId(), payload);
    }

    private String serialize(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialise admin listing payload", e);
        }
    }
}
