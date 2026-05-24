package com.dreamhomes.haven.promotion;
import com.dreamhomes.haven.admin.AdminAuditApi;
import com.dreamhomes.haven.admin.model.AdminAction;
import com.dreamhomes.haven.admin.model.AuditTargetType;
import com.dreamhomes.haven.listing.ListingService;
import com.dreamhomes.haven.listing.dto.ListingResponse;
import com.dreamhomes.haven.listing.model.ListingStatus;
import com.dreamhomes.haven.notification.NotificationApi;
import com.dreamhomes.haven.notification.model.NotificationKind;
import com.dreamhomes.haven.promotion.dto.CreatePromotionRequest;
import com.dreamhomes.haven.promotion.dto.PromotionMetricsResponse;
import com.dreamhomes.haven.promotion.dto.PromotionMetricsSummaryResponse;
import com.dreamhomes.haven.promotion.dto.PromotionPublicResponse;
import com.dreamhomes.haven.promotion.dto.PromotionResponse;
import com.dreamhomes.haven.promotion.exception.InvalidPromotionTargetException;
import com.dreamhomes.haven.promotion.exception.InvalidPromotionTransitionException;
import com.dreamhomes.haven.promotion.exception.InvalidPromotionWindowException;
import com.dreamhomes.haven.promotion.exception.NotPromotionOwnerException;
import com.dreamhomes.haven.promotion.exception.PromotionNotFoundException;
import com.dreamhomes.haven.promotion.model.Promotion;
import com.dreamhomes.haven.promotion.model.PromotionClick;
import com.dreamhomes.haven.promotion.model.PromotionImpression;
import com.dreamhomes.haven.promotion.model.PromotionPlacement;
import com.dreamhomes.haven.promotion.model.PromotionStatus;
import com.dreamhomes.haven.promotion.model.PromotionTargetType;
import com.dreamhomes.haven.user.dto.PublicUserProfile;
import com.dreamhomes.haven.user.model.Role;
import com.dreamhomes.haven.user.repository.UserRepository;
import com.dreamhomes.haven.user.service.UserProfileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;


@Service
@Slf4j
@RequiredArgsConstructor
public class PromotionService {

    private final PromotionRepository promotionRepository;
    private final PromotionImpressionRepository impressionRepository;
    private final PromotionClickRepository clickRepository;
    private final ListingService listingService;
    private final UserProfileService userProfileService;
    private final UserRepository userRepository;
    private final NotificationApi notificationApi;
    private final AdminAuditApi adminAuditApi;

    @Transactional
    public PromotionResponse request(Long callerId, CreatePromotionRequest request) {
        validateWindow(request.startsAt(), request.endsAt());
        validatePlacementForTarget(request.targetType(), request.placement());
        validateRequesterOwnsTarget(callerId, request.targetType(), request.targetId());

        Promotion saved = promotionRepository.save(Promotion.builder()
                .targetType(request.targetType())
                .listingId(request.targetType() == PromotionTargetType.LISTING ? request.targetId() : null)
                .agentUserId(request.targetType() == PromotionTargetType.AGENT ? request.targetId() : null)
                .placement(request.placement())
                .status(PromotionStatus.PENDING)
                .startsAt(request.startsAt())
                .endsAt(request.endsAt())
                .priority(0)
                .createdByUserId(callerId)
                .build());
        log.info("User {} requested promotionId={} target={}:{} placement={}",
                callerId, saved.getId(), saved.getTargetType(), targetId(saved), saved.getPlacement());
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public Page<PromotionResponse> listMine(Long callerId, Pageable pageable) {
        return promotionRepository.findByCreatedByUserIdOrderByCreatedAtDesc(callerId, pageable)
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public PromotionResponse findMineOrAdmin(Long callerId, Role role, Long promotionId) {
        Promotion promotion = load(promotionId);
        if (role != Role.ADMIN && !promotion.getCreatedByUserId().equals(callerId)) {
            throw new NotPromotionOwnerException();
        }
        return toResponse(promotion);
    }

    @Transactional(readOnly = true)
    public Page<PromotionPublicResponse> publicFor(PromotionPlacement placement, Pageable pageable) {
        Instant now = Instant.now();
        Page<Promotion> page = promotionRepository.findActiveForPlacement(placement, now, pageable);
        var visible = page.stream()
                .map(this::toPublicResponseIfSafe)
                .flatMap(Optional::stream)
                .toList();
        return new PageImpl<>(visible, pageable, visible.size());
    }

    @Transactional
    public void recordImpression(Long promotionId, PromotionPlacement placement, Long viewerUserId) {
        Promotion promotion = load(promotionId);
        validateTrackingPlacement(promotion, placement);
        impressionRepository.save(PromotionImpression.builder()
                .promotionId(promotionId)
                .viewerUserId(viewerUserId)
                .placement(placement)
                .build());
    }

    @Transactional
    public void recordClick(Long promotionId, PromotionPlacement placement, Long viewerUserId) {
        Promotion promotion = load(promotionId);
        validateTrackingPlacement(promotion, placement);
        clickRepository.save(PromotionClick.builder()
                .promotionId(promotionId)
                .viewerUserId(viewerUserId)
                .placement(placement)
                .build());
    }

    @Transactional(readOnly = true)
    public PromotionMetricsResponse metricsMineOrAdmin(Long callerId, Role role, Long promotionId) {
        Promotion promotion = load(promotionId);
        if (role != Role.ADMIN && !promotion.getCreatedByUserId().equals(callerId)) {
            throw new NotPromotionOwnerException();
        }
        return metricsFor(promotionId);
    }

    @Transactional(readOnly = true)
    public Page<PromotionResponse> adminSearch(PromotionStatus status, PromotionTargetType targetType,
                                               PromotionPlacement placement, Long createdByUserId,
                                               Pageable pageable) {
        return promotionRepository.adminSearch(status, targetType, placement, createdByUserId, pageable)
                .map(this::toResponse);
    }

    @Transactional
    public PromotionResponse approve(Long adminId, Long promotionId, Integer priority, String reason) {
        Promotion promotion = load(promotionId);
        if (promotion.getStatus() != PromotionStatus.PENDING && promotion.getStatus() != PromotionStatus.PAUSED) {
            throw new InvalidPromotionTransitionException();
        }
        if (toPublicResponseIfSafe(promotion).isEmpty()) {
            throw new InvalidPromotionTargetException();
        }
        Instant now = Instant.now();
        promotion.setStatus(PromotionStatus.ACTIVE);
        promotion.setPriority(priority == null ? 0 : priority);
        promotion.setApprovedByAdminId(adminId);
        promotion.setApprovedAt(now);
        promotion.setDecisionReason(blankToNull(reason));
        Promotion saved = promotionRepository.save(promotion);
        auditAndNotify(adminId, saved, AdminAction.PROMOTION_APPROVED, NotificationKind.PROMOTION_APPROVED, reason);
        return toResponse(saved);
    }

    @Transactional
    public PromotionResponse reject(Long adminId, Long promotionId, String reason) {
        Promotion promotion = load(promotionId);
        if (promotion.getStatus() != PromotionStatus.PENDING) {
            throw new InvalidPromotionTransitionException();
        }
        requireReason(reason);
        promotion.setStatus(PromotionStatus.REJECTED);
        promotion.setApprovedByAdminId(adminId);
        promotion.setDecisionReason(reason.trim());
        Promotion saved = promotionRepository.save(promotion);
        auditAndNotify(adminId, saved, AdminAction.PROMOTION_REJECTED, NotificationKind.PROMOTION_REJECTED, reason);
        return toResponse(saved);
    }

    @Transactional
    public PromotionResponse pause(Long adminId, Long promotionId, String reason) {
        Promotion promotion = load(promotionId);
        if (promotion.getStatus() != PromotionStatus.ACTIVE) {
            throw new InvalidPromotionTransitionException();
        }
        requireReason(reason);
        promotion.setStatus(PromotionStatus.PAUSED);
        promotion.setDecisionReason(reason.trim());
        Promotion saved = promotionRepository.save(promotion);
        auditAndNotify(adminId, saved, AdminAction.PROMOTION_PAUSED, NotificationKind.PROMOTION_PAUSED, reason);
        return toResponse(saved);
    }

    @Transactional
    public PromotionResponse resume(Long adminId, Long promotionId, String reason) {
        Promotion promotion = load(promotionId);
        if (promotion.getStatus() != PromotionStatus.PAUSED) {
            throw new InvalidPromotionTransitionException();
        }
        if (toPublicResponseIfSafe(promotion).isEmpty()) {
            throw new InvalidPromotionTargetException();
        }
        promotion.setStatus(PromotionStatus.ACTIVE);
        promotion.setDecisionReason(blankToNull(reason));
        Promotion saved = promotionRepository.save(promotion);
        auditAndNotify(adminId, saved, AdminAction.PROMOTION_RESUMED, NotificationKind.PROMOTION_RESUMED, reason);
        return toResponse(saved);
    }

    @Transactional
    public PromotionResponse revoke(Long adminId, Long promotionId, String reason) {
        Promotion promotion = load(promotionId);
        if (promotion.getStatus() == PromotionStatus.REVOKED || promotion.getStatus() == PromotionStatus.REJECTED) {
            throw new InvalidPromotionTransitionException();
        }
        requireReason(reason);
        promotion.setStatus(PromotionStatus.REVOKED);
        promotion.setDecisionReason(reason.trim());
        Promotion saved = promotionRepository.save(promotion);
        auditAndNotify(adminId, saved, AdminAction.PROMOTION_REVOKED, NotificationKind.PROMOTION_REVOKED, reason);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public PromotionMetricsSummaryResponse adminMetricsSummary() {
        long impressions = impressionRepository.countAllImpressions();
        long clicks = clickRepository.countAllClicks();
        return new PromotionMetricsSummaryResponse(
                promotionRepository.countByStatus(PromotionStatus.ACTIVE),
                impressions,
                clicks,
                clickThroughRate(clicks, impressions));
    }

    private Optional<PromotionPublicResponse> toPublicResponseIfSafe(Promotion promotion) {
        if (promotion.getTargetType() == PromotionTargetType.LISTING) {
            return publicListingPromotion(promotion);
        }
        return publicAgentPromotion(promotion);
    }

    private Optional<PromotionPublicResponse> publicListingPromotion(Promotion promotion) {
        ListingResponse listing;
        try {
            listing = listingService.findById(promotion.getListingId());
        } catch (RuntimeException missing) {
            return Optional.empty();
        }
        if (listing.status() != ListingStatus.LIVE) {
            return Optional.empty();
        }
        boolean ownerSafe = userRepository.findById(listing.ownerId())
                .filter(u -> u.getSuspendedAt() == null)
                .filter(u -> u.getAccountDeletedAt() == null)
                .isPresent();
        if (!ownerSafe) {
            return Optional.empty();
        }
        return Optional.of(new PromotionPublicResponse(
                promotion.getId(),
                promotion.getTargetType(),
                targetId(promotion),
                promotion.getPlacement(),
                promotion.getPlacement().label(),
                listing,
                null));
    }

    private Optional<PromotionPublicResponse> publicAgentPromotion(Promotion promotion) {
        PublicUserProfile profile;
        try {
            profile = userProfileService.findPublicProfile(promotion.getAgentUserId());
        } catch (RuntimeException missing) {
            return Optional.empty();
        }
        if (profile.role() != Role.AGENT || profile.suspended()) {
            return Optional.empty();
        }
        return Optional.of(new PromotionPublicResponse(
                promotion.getId(),
                promotion.getTargetType(),
                targetId(promotion),
                promotion.getPlacement(),
                promotion.getPlacement().label(),
                null,
                profile));
    }

    private PromotionMetricsResponse metricsFor(Long promotionId) {
        long impressions = impressionRepository.countByPromotionId(promotionId);
        long clicks = clickRepository.countByPromotionId(promotionId);
        return new PromotionMetricsResponse(promotionId, impressions, clicks, clickThroughRate(clicks, impressions));
    }

    private static double clickThroughRate(long clicks, long impressions) {
        return impressions == 0 ? 0.0 : ((double) clicks) / impressions;
    }

    private Promotion load(Long promotionId) {
        return promotionRepository.findById(promotionId)
                .orElseThrow(() -> new PromotionNotFoundException(promotionId));
    }

    private void validateWindow(Instant startsAt, Instant endsAt) {
        if (!endsAt.isAfter(startsAt)) {
            throw new InvalidPromotionWindowException();
        }
    }

    private void validatePlacementForTarget(PromotionTargetType targetType, PromotionPlacement placement) {
        if (placement == PromotionPlacement.LISTING_SEARCH_TOP && targetType != PromotionTargetType.LISTING) {
            throw new InvalidPromotionTargetException();
        }
        if (placement == PromotionPlacement.AGENT_DIRECTORY_TOP && targetType != PromotionTargetType.AGENT) {
            throw new InvalidPromotionTargetException();
        }
    }

    private void validateRequesterOwnsTarget(Long callerId, PromotionTargetType targetType, Long targetId) {
        if (targetType == PromotionTargetType.LISTING) {
            Long ownerId = listingService.ownerOf(targetId)
                    .orElseThrow(() -> new InvalidPromotionTargetException());
            if (!ownerId.equals(callerId)) {
                throw new InvalidPromotionTargetException();
            }
            return;
        }
        Role role = userProfileService.roleOf(targetId)
                .orElseThrow(InvalidPromotionTargetException::new);
        if (!targetId.equals(callerId) || role != Role.AGENT) {
            throw new InvalidPromotionTargetException();
        }
    }

    private static void validateTrackingPlacement(Promotion promotion, PromotionPlacement placement) {
        if (promotion.getPlacement() != placement) {
            throw new InvalidPromotionTargetException();
        }
    }

    private void auditAndNotify(Long adminId, Promotion promotion, AdminAction action,
                                NotificationKind kind, String reason) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("targetType", promotion.getTargetType().name());
        metadata.put("targetId", targetId(promotion));
        metadata.put("placement", promotion.getPlacement().name());
        metadata.put("status", promotion.getStatus().name());
        if (reason != null && !reason.isBlank()) {
            metadata.put("reason", reason.trim());
        }
        adminAuditApi.record(adminId, action, AuditTargetType.PROMOTION, promotion.getId(), metadata);
        notificationApi.recordSync(kind, promotion.getCreatedByUserId(), metadata);
    }

    private static void requireReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Promotion decision reason is required");
        }
    }

    private static String blankToNull(String reason) {
        return reason == null || reason.isBlank() ? null : reason.trim();
    }

    private PromotionResponse toResponse(Promotion promotion) {
        return new PromotionResponse(
                promotion.getId(),
                promotion.getTargetType(),
                targetId(promotion),
                promotion.getPlacement(),
                promotion.getStatus(),
                promotion.getStartsAt(),
                promotion.getEndsAt(),
                promotion.getPriority(),
                promotion.getCreatedByUserId(),
                promotion.getApprovedByAdminId(),
                promotion.getApprovedAt(),
                promotion.getDecisionReason(),
                promotion.getCreatedAt(),
                promotion.getUpdatedAt());
    }

    private static Long targetId(Promotion promotion) {
        return promotion.getTargetType() == PromotionTargetType.LISTING
                ? promotion.getListingId()
                : promotion.getAgentUserId();
    }
}
