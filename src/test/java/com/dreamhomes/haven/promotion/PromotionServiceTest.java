package com.dreamhomes.haven.promotion;
import com.dreamhomes.haven.admin.AdminAuditApi;
import com.dreamhomes.haven.admin.model.AdminAction;
import com.dreamhomes.haven.admin.model.AuditTargetType;
import com.dreamhomes.haven.listing.ListingService;
import com.dreamhomes.haven.listing.dto.ListingResponse;
import com.dreamhomes.haven.listing.model.ListingStatus;
import com.dreamhomes.haven.listing.model.ListingType;
import com.dreamhomes.haven.notification.NotificationApi;
import com.dreamhomes.haven.notification.model.NotificationKind;
import com.dreamhomes.haven.promotion.dto.CreatePromotionRequest;
import com.dreamhomes.haven.promotion.exception.InvalidPromotionTargetException;
import com.dreamhomes.haven.promotion.exception.InvalidPromotionTransitionException;
import com.dreamhomes.haven.promotion.exception.InvalidPromotionWindowException;
import com.dreamhomes.haven.promotion.exception.PromotionNotFoundException;
import com.dreamhomes.haven.promotion.model.Promotion;
import com.dreamhomes.haven.promotion.model.PromotionClick;
import com.dreamhomes.haven.promotion.model.PromotionImpression;
import com.dreamhomes.haven.promotion.model.PromotionPlacement;
import com.dreamhomes.haven.promotion.model.PromotionStatus;
import com.dreamhomes.haven.promotion.model.PromotionTargetType;
import com.dreamhomes.haven.user.dto.PublicUserProfile;
import com.dreamhomes.haven.user.model.Role;
import com.dreamhomes.haven.user.model.User;
import com.dreamhomes.haven.user.repository.UserRepository;
import com.dreamhomes.haven.user.service.UserProfileService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


@ExtendWith(MockitoExtension.class)
class PromotionServiceTest {

    @Mock PromotionRepository promotionRepository;
    @Mock PromotionImpressionRepository impressionRepository;
    @Mock PromotionClickRepository clickRepository;
    @Mock ListingService listingService;
    @Mock UserProfileService userProfileService;
    @Mock UserRepository userRepository;
    @Mock NotificationApi notificationApi;
    @Mock AdminAuditApi adminAuditApi;

    @InjectMocks PromotionService promotionService;

    @Test
    void ownerCanRequestListingPromotionForOwnListing() {
        when(listingService.ownerOf(44L)).thenReturn(Optional.of(7L));
        when(promotionRepository.save(any(Promotion.class))).thenAnswer(invocation -> {
            Promotion p = invocation.getArgument(0);
            p.setId(12L);
            return p;
        });

        var response = promotionService.request(7L, new CreatePromotionRequest(
                PromotionTargetType.LISTING,
                44L,
                PromotionPlacement.HOMEPAGE_FEATURED,
                Instant.parse("2026-06-01T00:00:00Z"),
                Instant.parse("2026-06-15T00:00:00Z")));

        assertThat(response.id()).isEqualTo(12L);
        assertThat(response.status()).isEqualTo(PromotionStatus.PENDING);
        assertThat(response.createdByUserId()).isEqualTo(7L);

        ArgumentCaptor<Promotion> captor = ArgumentCaptor.forClass(Promotion.class);
        verify(promotionRepository).save(captor.capture());
        assertThat(captor.getValue().getTargetType()).isEqualTo(PromotionTargetType.LISTING);
        assertThat(captor.getValue().getListingId()).isEqualTo(44L);
        assertThat(captor.getValue().getAgentUserId()).isNull();
    }

    @Test
    void agentCanRequestAgentPromotionForSelf() {
        when(userProfileService.roleOf(9L)).thenReturn(Optional.of(Role.AGENT));
        when(promotionRepository.save(any(Promotion.class))).thenAnswer(invocation -> {
            Promotion p = invocation.getArgument(0);
            p.setId(13L);
            return p;
        });

        var response = promotionService.request(9L, new CreatePromotionRequest(
                PromotionTargetType.AGENT,
                9L,
                PromotionPlacement.AGENT_DIRECTORY_TOP,
                Instant.parse("2026-06-01T00:00:00Z"),
                Instant.parse("2026-06-15T00:00:00Z")));

        assertThat(response.id()).isEqualTo(13L);
        assertThat(response.targetId()).isEqualTo(9L);

        ArgumentCaptor<Promotion> captor = ArgumentCaptor.forClass(Promotion.class);
        verify(promotionRepository).save(captor.capture());
        assertThat(captor.getValue().getTargetType()).isEqualTo(PromotionTargetType.AGENT);
        assertThat(captor.getValue().getListingId()).isNull();
        assertThat(captor.getValue().getAgentUserId()).isEqualTo(9L);
    }

    @Test
    void listingSearchPlacementRejectsAgentTarget() {
        assertThatThrownBy(() -> promotionService.request(9L, new CreatePromotionRequest(
                PromotionTargetType.AGENT,
                9L,
                PromotionPlacement.LISTING_SEARCH_TOP,
                Instant.parse("2026-06-01T00:00:00Z"),
                Instant.parse("2026-06-15T00:00:00Z"))))
                .isInstanceOf(InvalidPromotionTargetException.class);
    }

    @Test
    void agentDirectoryPlacementRejectsListingTarget() {
        assertThatThrownBy(() -> promotionService.request(7L, new CreatePromotionRequest(
                PromotionTargetType.LISTING,
                44L,
                PromotionPlacement.AGENT_DIRECTORY_TOP,
                Instant.parse("2026-06-01T00:00:00Z"),
                Instant.parse("2026-06-15T00:00:00Z"))))
                .isInstanceOf(InvalidPromotionTargetException.class);
    }

    @Test
    void homepagePlacementAcceptsListingAndAgentTargets() {
        when(listingService.ownerOf(44L)).thenReturn(Optional.of(7L));
        when(userProfileService.roleOf(9L)).thenReturn(Optional.of(Role.AGENT));
        when(promotionRepository.save(any(Promotion.class))).thenAnswer(invocation -> {
            Promotion p = invocation.getArgument(0);
            p.setId(p.getTargetType() == PromotionTargetType.LISTING ? 12L : 13L);
            return p;
        });

        var listingResponse = promotionService.request(7L, request(
                PromotionTargetType.LISTING, 44L, PromotionPlacement.HOMEPAGE_FEATURED));
        var agentResponse = promotionService.request(9L, request(
                PromotionTargetType.AGENT, 9L, PromotionPlacement.HOMEPAGE_FEATURED));

        assertThat(listingResponse.targetId()).isEqualTo(44L);
        assertThat(agentResponse.targetId()).isEqualTo(9L);
    }

    @Test
    void rejectsWindowWhenEndIsNotAfterStart() {
        assertThatThrownBy(() -> promotionService.request(7L, new CreatePromotionRequest(
                PromotionTargetType.LISTING,
                44L,
                PromotionPlacement.HOMEPAGE_FEATURED,
                Instant.parse("2026-06-01T00:00:00Z"),
                Instant.parse("2026-06-01T00:00:00Z"))))
                .isInstanceOf(InvalidPromotionWindowException.class);
    }

    @Test
    void ownerCannotPromoteAnotherOwnersListing() {
        when(listingService.ownerOf(44L)).thenReturn(Optional.of(8L));

        assertThatThrownBy(() -> promotionService.request(7L, request(
                PromotionTargetType.LISTING, 44L, PromotionPlacement.HOMEPAGE_FEATURED)))
                .isInstanceOf(InvalidPromotionTargetException.class);
    }

    @Test
    void applicantCannotPromoteAListing() {
        when(listingService.ownerOf(44L)).thenReturn(Optional.of(7L));

        assertThatThrownBy(() -> promotionService.request(11L, request(
                PromotionTargetType.LISTING, 44L, PromotionPlacement.HOMEPAGE_FEATURED)))
                .isInstanceOf(InvalidPromotionTargetException.class);
    }

    @Test
    void nonAgentCannotRequestAgentPromotion() {
        when(userProfileService.roleOf(9L)).thenReturn(Optional.of(Role.OWNER));

        assertThatThrownBy(() -> promotionService.request(9L, request(
                PromotionTargetType.AGENT, 9L, PromotionPlacement.AGENT_DIRECTORY_TOP)))
                .isInstanceOf(InvalidPromotionTargetException.class);
    }

    @Test
    void agentCannotRequestPromotionForAnotherAgent() {
        when(userProfileService.roleOf(10L)).thenReturn(Optional.of(Role.AGENT));

        assertThatThrownBy(() -> promotionService.request(9L, request(
                PromotionTargetType.AGENT, 10L, PromotionPlacement.AGENT_DIRECTORY_TOP)))
                .isInstanceOf(InvalidPromotionTargetException.class);
    }

    @Test
    void publicPlacementHidesTakenDownListingEvenWhenPromotionIsActive() {
        Promotion promotion = activeListingPromotion();
        when(promotionRepository.findActiveForPlacement(
                any(PromotionPlacement.class), any(Instant.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(promotion)));
        when(listingService.findById(44L)).thenReturn(listing(ListingStatus.TAKEN_DOWN));

        var page = promotionService.publicFor(PromotionPlacement.HOMEPAGE_FEATURED, Pageable.unpaged());

        assertThat(page.getContent()).isEmpty();
    }

    @Test
    void publicPlacementReturnsLiveListingWhenOwnerIsSafe() {
        Promotion promotion = activeListingPromotion();
        
        when(promotionRepository.findActiveForPlacement(
                any(PromotionPlacement.class), any(Instant.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(promotion)));

        when(listingService.findById(44L)).thenReturn(listing(ListingStatus.LIVE));
        when(userRepository.findById(7L)).thenReturn(Optional.of(User.builder()
                .id(7L)
                .role(Role.OWNER)
                .email("owner@example.com")
                .passwordHash("hash")
                .fullName("Owner Example")
                .displayName("Owner")
                .build()));

        var page = promotionService.publicFor(PromotionPlacement.HOMEPAGE_FEATURED, Pageable.unpaged());

        assertThat(page.getContent()).hasSize(1);
        var promoted = page.getContent().getFirst();
        assertThat(promoted.promotionId()).isEqualTo(12L);
        assertThat(promoted.label()).isEqualTo("Featured");
        assertThat(promoted.targetType()).isEqualTo(PromotionTargetType.LISTING);
        assertThat(promoted.listing().id()).isEqualTo(44L);
        assertThat(promoted.agent()).isNull();
    }

    @Test
    void publicPlacementReturnsAgentWhenAgentIsSafe() {
        Promotion promotion = Promotion.builder()
                .id(13L)
                .targetType(PromotionTargetType.AGENT)
                .agentUserId(9L)
                .placement(PromotionPlacement.AGENT_DIRECTORY_TOP)
                .status(PromotionStatus.ACTIVE)
                .startsAt(Instant.parse("2026-06-01T00:00:00Z"))
                .endsAt(Instant.parse("2026-06-15T00:00:00Z"))
                .priority(5)
                .createdByUserId(9L)
                .approvedByAdminId(1L)
                .approvedAt(Instant.parse("2026-05-24T10:00:00Z"))
                .build();

        when(promotionRepository.findActiveForPlacement(
                any(PromotionPlacement.class), any(Instant.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(promotion)));
        when(userProfileService.findPublicProfile(9L)).thenReturn(agentProfile(false));

        var page = promotionService.publicFor(PromotionPlacement.AGENT_DIRECTORY_TOP, Pageable.unpaged());

        assertThat(page.getContent()).hasSize(1);
        var promoted = page.getContent().getFirst();
        assertThat(promoted.promotionId()).isEqualTo(13L);
        assertThat(promoted.label()).isEqualTo("Featured");
        assertThat(promoted.targetType()).isEqualTo(PromotionTargetType.AGENT);
        assertThat(promoted.targetId()).isEqualTo(9L);
        assertThat(promoted.agent().id()).isEqualTo(9L);
        assertThat(promoted.listing()).isNull();
    }

    @Test
    void metricsCountsImpressionsClicksAndCtr() {
        when(promotionRepository.findById(12L)).thenReturn(Optional.of(Promotion.builder()
                .id(12L)
                .createdByUserId(7L)
                .build()));
        when(impressionRepository.countByPromotionId(12L)).thenReturn(1000L);
        when(clickRepository.countByPromotionId(12L)).thenReturn(80L);

        var metrics = promotionService.metricsMineOrAdmin(7L, Role.OWNER, 12L);

        assertThat(metrics.impressions()).isEqualTo(1000);
        assertThat(metrics.clicks()).isEqualTo(80);
        assertThat(metrics.clickThroughRate()).isEqualTo(0.08);
    }

    @Test
    void approvingPromotionActivatesItAndNotifiesOwner() {
        Promotion promotion = activeListingPromotion();
        promotion.setStatus(PromotionStatus.PENDING);
        promotion.setApprovedByAdminId(null);
        promotion.setApprovedAt(null);
        when(promotionRepository.findById(12L)).thenReturn(Optional.of(promotion));
        when(listingService.findById(44L)).thenReturn(listing(ListingStatus.LIVE));
        when(userRepository.findById(7L)).thenReturn(Optional.of(User.builder()
                .id(7L)
                .role(Role.OWNER)
                .email("owner@example.com")
                .passwordHash("hash")
                .fullName("Owner Example")
                .displayName("Owner")
                .build()));
        when(promotionRepository.save(any(Promotion.class))).thenAnswer(invocation -> invocation.getArgument(0));

        promotionService.approve(1L, 12L, 9, "Good homepage candidate");

        ArgumentCaptor<Promotion> captor = ArgumentCaptor.forClass(Promotion.class);
        verify(promotionRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(PromotionStatus.ACTIVE);
        assertThat(captor.getValue().getPriority()).isEqualTo(9);
        verify(adminAuditApi).record(eq(1L), eq(AdminAction.PROMOTION_APPROVED),
                eq(AuditTargetType.PROMOTION), eq(12L), any());
        verify(notificationApi).recordSync(eq(NotificationKind.PROMOTION_APPROVED), eq(7L), any());
    }

    @Test
    void cannotApproveAlreadyRejectedPromotion() {
        when(promotionRepository.findById(12L))
                .thenReturn(Optional.of(activeListingPromotionWithStatus(PromotionStatus.REJECTED)));

        assertThatThrownBy(() -> promotionService.approve(1L, 12L, 1, null))
                .isInstanceOf(InvalidPromotionTransitionException.class);
    }

    @Test
    void cannotRejectAlreadyActivePromotion() {
        when(promotionRepository.findById(12L)).thenReturn(Optional.of(activeListingPromotion()));

        assertThatThrownBy(() -> promotionService.reject(1L, 12L, "No longer eligible"))
                .isInstanceOf(InvalidPromotionTransitionException.class);
    }

    @Test
    void cannotPausePendingRejectedOrRevokedPromotion() {
        for (PromotionStatus status : List.of(PromotionStatus.PENDING, PromotionStatus.REJECTED, PromotionStatus.REVOKED)) {
            when(promotionRepository.findById(12L))
                    .thenReturn(Optional.of(activeListingPromotionWithStatus(status)));

            assertThatThrownBy(() -> promotionService.pause(1L, 12L, "Hold"))
                    .isInstanceOf(InvalidPromotionTransitionException.class);
        }
    }

    @Test
    void cannotResumeRejectedOrRevokedPromotion() {
        for (PromotionStatus status : List.of(PromotionStatus.REJECTED, PromotionStatus.REVOKED)) {
            when(promotionRepository.findById(12L))
                    .thenReturn(Optional.of(activeListingPromotionWithStatus(status)));

            assertThatThrownBy(() -> promotionService.resume(1L, 12L, "Back"))
                    .isInstanceOf(InvalidPromotionTransitionException.class);
        }
    }

    @Test
    void cannotRevokeAlreadyRejectedOrRevokedPromotion() {
        for (PromotionStatus status : List.of(PromotionStatus.REJECTED, PromotionStatus.REVOKED)) {
            when(promotionRepository.findById(12L))
                    .thenReturn(Optional.of(activeListingPromotionWithStatus(status)));

            assertThatThrownBy(() -> promotionService.revoke(1L, 12L, "Stop"))
                    .isInstanceOf(InvalidPromotionTransitionException.class);
        }
    }

    @Test
    void resumeRechecksSafetyBeforeMakingPromotionActiveAgain() {
        when(promotionRepository.findById(12L))
                .thenReturn(Optional.of(activeListingPromotionWithStatus(PromotionStatus.PAUSED)));
        when(listingService.findById(44L)).thenReturn(listing(ListingStatus.TAKEN_DOWN));

        assertThatThrownBy(() -> promotionService.resume(1L, 12L, "Safe again"))
                .isInstanceOf(InvalidPromotionTargetException.class);
        verify(promotionRepository, never()).save(any(Promotion.class));
    }

    @Test
    void rejectPauseResumeAndRevokeWriteExpectedAuditAndNotificationKinds() {
        assertDecisionAction(PromotionStatus.PENDING,
                () -> promotionService.reject(1L, 12L, "Bad fit"),
                AdminAction.PROMOTION_REJECTED,
                NotificationKind.PROMOTION_REJECTED,
                PromotionStatus.REJECTED,
                "Bad fit");
        assertDecisionAction(PromotionStatus.ACTIVE,
                () -> promotionService.pause(1L, 12L, "Budget hold"),
                AdminAction.PROMOTION_PAUSED,
                NotificationKind.PROMOTION_PAUSED,
                PromotionStatus.PAUSED,
                "Budget hold");
        assertDecisionAction(PromotionStatus.PAUSED,
                () -> {
                    when(listingService.findById(44L)).thenReturn(listing(ListingStatus.LIVE));
                    when(userRepository.findById(7L)).thenReturn(Optional.of(safeOwner()));
                    promotionService.resume(1L, 12L, "Back live");
                },
                AdminAction.PROMOTION_RESUMED,
                NotificationKind.PROMOTION_RESUMED,
                PromotionStatus.ACTIVE,
                "Back live");
        assertDecisionAction(PromotionStatus.ACTIVE,
                () -> promotionService.revoke(1L, 12L, "Policy"),
                AdminAction.PROMOTION_REVOKED,
                NotificationKind.PROMOTION_REVOKED,
                PromotionStatus.REVOKED,
                "Policy");
    }

    @Test
    void auditMetadataIncludesTargetPlacementStatusAndReasonWhenPresent() {
        when(promotionRepository.findById(12L))
                .thenReturn(Optional.of(activeListingPromotionWithStatus(PromotionStatus.PENDING)));
        when(promotionRepository.save(any(Promotion.class))).thenAnswer(invocation -> invocation.getArgument(0));

        promotionService.reject(1L, 12L, "Unsafe target");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> metadata = ArgumentCaptor.forClass(Map.class);
        verify(adminAuditApi).record(eq(1L), eq(AdminAction.PROMOTION_REJECTED),
                eq(AuditTargetType.PROMOTION), eq(12L), metadata.capture());
        assertThat(metadata.getValue())
                .containsEntry("targetType", "LISTING")
                .containsEntry("targetId", 44L)
                .containsEntry("placement", "HOMEPAGE_FEATURED")
                .containsEntry("status", "REJECTED")
                .containsEntry("reason", "Unsafe target");
    }

    @Test
    void impressionAndClickRejectWrongPlacement() {
        when(promotionRepository.findById(12L)).thenReturn(Optional.of(activeListingPromotion()));

        assertThatThrownBy(() -> promotionService.recordImpression(
                12L, PromotionPlacement.AGENT_DIRECTORY_TOP, null))
                .isInstanceOf(InvalidPromotionTargetException.class);
        assertThatThrownBy(() -> promotionService.recordClick(
                12L, PromotionPlacement.AGENT_DIRECTORY_TOP, null))
                .isInstanceOf(InvalidPromotionTargetException.class);
    }

    @Test
    void impressionAndClickRejectMissingPromotion() {
        when(promotionRepository.findById(12L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> promotionService.recordImpression(
                12L, PromotionPlacement.HOMEPAGE_FEATURED, null))
                .isInstanceOf(PromotionNotFoundException.class);
        assertThatThrownBy(() -> promotionService.recordClick(
                12L, PromotionPlacement.HOMEPAGE_FEATURED, 7L))
                .isInstanceOf(PromotionNotFoundException.class);
    }

    @Test
    void anonymousImpressionStoresNullViewerAndAuthenticatedImpressionStoresUserId() {
        when(promotionRepository.findById(12L)).thenReturn(Optional.of(activeListingPromotion()));

        promotionService.recordImpression(12L, PromotionPlacement.HOMEPAGE_FEATURED, null);
        promotionService.recordImpression(12L, PromotionPlacement.HOMEPAGE_FEATURED, 7L);

        ArgumentCaptor<PromotionImpression> captor = ArgumentCaptor.forClass(PromotionImpression.class);
        verify(impressionRepository, org.mockito.Mockito.times(2)).save(captor.capture());
        assertThat(captor.getAllValues()).extracting(PromotionImpression::getViewerUserId)
                .containsExactly(null, 7L);
    }

    @Test
    void authenticatedClickStoresUserId() {
        when(promotionRepository.findById(12L)).thenReturn(Optional.of(activeListingPromotion()));

        promotionService.recordClick(12L, PromotionPlacement.HOMEPAGE_FEATURED, 7L);

        ArgumentCaptor<PromotionClick> captor = ArgumentCaptor.forClass(PromotionClick.class);
        verify(clickRepository).save(captor.capture());
        assertThat(captor.getValue().getViewerUserId()).isEqualTo(7L);
    }

    @Test
    void trackingStillWorksWhenPromotionIsNoLongerVisible() {
        when(promotionRepository.findById(12L))
                .thenReturn(Optional.of(activeListingPromotionWithStatus(PromotionStatus.PAUSED)));

        promotionService.recordImpression(12L, PromotionPlacement.HOMEPAGE_FEATURED, null);
        promotionService.recordClick(12L, PromotionPlacement.HOMEPAGE_FEATURED, null);

        verify(impressionRepository).save(any(PromotionImpression.class));
        verify(clickRepository).save(any(PromotionClick.class));
    }

    @Test
    void adminSearchPassesAllFiltersToRepository() {
        when(promotionRepository.adminSearch(any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));

        promotionService.adminSearch(PromotionStatus.ACTIVE, PromotionTargetType.LISTING,
                PromotionPlacement.LISTING_SEARCH_TOP, 7L, Pageable.unpaged());

        verify(promotionRepository).adminSearch(
                PromotionStatus.ACTIVE,
                PromotionTargetType.LISTING,
                PromotionPlacement.LISTING_SEARCH_TOP,
                7L,
                Pageable.unpaged());
    }

    @Test
    void metricsSummaryCountsActivePromotionsAndZeroCtrWhenThereAreNoImpressions() {
        when(promotionRepository.countByStatus(PromotionStatus.ACTIVE)).thenReturn(3L);
        when(impressionRepository.countAllImpressions()).thenReturn(0L);
        when(clickRepository.countAllClicks()).thenReturn(12L);

        var summary = promotionService.adminMetricsSummary();

        assertThat(summary.totalActivePromotions()).isEqualTo(3);
        assertThat(summary.averageClickThroughRate()).isEqualTo(0.0);
    }

    @Test
    void metricsSummaryCtrUsesTotalClicksOverTotalImpressions() {
        when(promotionRepository.countByStatus(PromotionStatus.ACTIVE)).thenReturn(2L);
        when(impressionRepository.countAllImpressions()).thenReturn(1000L);
        when(clickRepository.countAllClicks()).thenReturn(80L);

        var summary = promotionService.adminMetricsSummary();

        assertThat(summary.totalActivePromotions()).isEqualTo(2);
        assertThat(summary.totalClicks()).isEqualTo(80);
        assertThat(summary.totalImpressions()).isEqualTo(1000);
        assertThat(summary.averageClickThroughRate()).isEqualTo(0.08);
    }

    private static Promotion activeListingPromotion() {
        return Promotion.builder()
                .id(12L)
                .targetType(PromotionTargetType.LISTING)
                .listingId(44L)
                .placement(PromotionPlacement.HOMEPAGE_FEATURED)
                .status(PromotionStatus.ACTIVE)
                .startsAt(Instant.parse("2026-06-01T00:00:00Z"))
                .endsAt(Instant.parse("2026-06-15T00:00:00Z"))
                .priority(5)
                .createdByUserId(7L)
                .approvedByAdminId(1L)
                .approvedAt(Instant.parse("2026-05-24T10:00:00Z"))
                .build();
    }

    private static Promotion activeListingPromotionWithStatus(PromotionStatus status) {
        Promotion promotion = activeListingPromotion();
        promotion.setStatus(status);
        return promotion;
    }

    private static CreatePromotionRequest request(PromotionTargetType targetType, Long targetId,
                                                  PromotionPlacement placement) {
        return new CreatePromotionRequest(
                targetType,
                targetId,
                placement,
                Instant.parse("2026-06-01T00:00:00Z"),
                Instant.parse("2026-06-15T00:00:00Z"));
    }

    private void assertDecisionAction(PromotionStatus initialStatus, Runnable action,
                                      AdminAction expectedAction, NotificationKind expectedKind,
                                      PromotionStatus expectedStatus, String reason) {
        org.mockito.Mockito.reset(promotionRepository, listingService, userRepository, adminAuditApi, notificationApi);
        when(promotionRepository.findById(12L))
                .thenReturn(Optional.of(activeListingPromotionWithStatus(initialStatus)));
        when(promotionRepository.save(any(Promotion.class))).thenAnswer(invocation -> invocation.getArgument(0));

        action.run();

        ArgumentCaptor<Promotion> promotionCaptor = ArgumentCaptor.forClass(Promotion.class);
        verify(promotionRepository).save(promotionCaptor.capture());
        assertThat(promotionCaptor.getValue().getStatus()).isEqualTo(expectedStatus);
        assertThat(promotionCaptor.getValue().getDecisionReason()).isEqualTo(reason);
        verify(adminAuditApi).record(eq(1L), eq(expectedAction), eq(AuditTargetType.PROMOTION),
                eq(12L), any());
        verify(notificationApi).recordSync(eq(expectedKind), eq(7L), any());
    }

    private static ListingResponse listing(ListingStatus status) {
        return new ListingResponse(
                44L,
                22L,
                7L,
                ListingType.RENT,
                BigDecimal.valueOf(1_500_000),
                "NGN",
                null,
                null,
                null,
                "2 Bedroom Flat in Lekki",
                "Clean apartment",
                "Bright Lekki flat",
                null,
                null,
                false,
                status,
                null,
                0L,
                Instant.parse("2026-05-01T00:00:00Z"),
                Instant.parse("2026-05-01T00:00:00Z"),
                null,
                null,
                0L,
                null,
                null,
                null,
                null,
                null);
    }

    private static User safeOwner() {
        return User.builder()
                .id(7L)
                .role(Role.OWNER)
                .email("owner@example.com")
                .passwordHash("hash")
                .fullName("Owner Example")
                .displayName("Owner")
                .build();
    }

    private static PublicUserProfile agentProfile(boolean suspended) {
        return new PublicUserProfile(
                9L,
                "Agent Example",
                "Agent",
                Role.AGENT,
                Instant.parse("2026-05-01T00:00:00Z"),
                Instant.parse("2026-05-01T00:00:00Z"),
                suspended,
                null,
                0L,
                0L,
                null,
                Instant.parse("2026-05-01T00:00:00Z"),
                List.of(),
                List.of(),
                List.of(),
                null,
                null,
                null,
                List.of());
    }
}