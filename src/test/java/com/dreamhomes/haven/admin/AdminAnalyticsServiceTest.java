package com.dreamhomes.haven.admin;

import com.dreamhomes.haven.admin.dto.AnalyticsSummaryResponse;
import com.dreamhomes.haven.admin.service.AdminAnalyticsService;
import com.dreamhomes.haven.listing.ListingRepository;
import com.dreamhomes.haven.listing.model.ListingStatus;
import com.dreamhomes.haven.offer.OfferRepository;
import com.dreamhomes.haven.offer.model.OfferStatus;
import com.dreamhomes.haven.user.repository.UserRepository;
import com.dreamhomes.haven.verification.VerificationRepository;
import com.dreamhomes.haven.verification.model.VerificationStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * Confirms {@link AdminAnalyticsService#summary()} maps each of its six count queries
 * to the correct field on the response — and that we don't issue any extra queries
 * we'd then have to explain in a code review.
 */
@ExtendWith(MockitoExtension.class)
class AdminAnalyticsServiceTest {

    @Mock UserRepository userRepository;
    @Mock ListingRepository listingRepository;
    @Mock VerificationRepository verificationRepository;
    @Mock OfferRepository offerRepository;

    @Test
    void summaryAggregatesEveryCountIntoTheResponse() {
        when(userRepository.count()).thenReturn(1284L);
        when(userRepository.countBySuspendedAtIsNotNull()).thenReturn(3L);
        when(listingRepository.countByStatus(ListingStatus.LIVE)).thenReturn(412L);
        when(listingRepository.countByStatus(ListingStatus.CLOSED)).thenReturn(187L);
        when(verificationRepository.countByStatus(VerificationStatus.PENDING)).thenReturn(8L);
        when(offerRepository.countByStatus(OfferStatus.PENDING)).thenReturn(47L);

        AdminAnalyticsService service = new AdminAnalyticsService(
                userRepository, listingRepository, verificationRepository, offerRepository);

        AnalyticsSummaryResponse summary = service.summary();

        assertThat(summary.totalUsers()).isEqualTo(1284L);
        assertThat(summary.suspendedUsers()).isEqualTo(3L);
        assertThat(summary.openListings()).isEqualTo(412L);
        assertThat(summary.closedListings()).isEqualTo(187L);
        assertThat(summary.pendingVerifications()).isEqualTo(8L);
        assertThat(summary.pendingOffers()).isEqualTo(47L);

        // Belt-and-suspenders: assert exactly six queries, no extras. If a future
        // change adds a query without adding a field, this test fails and the author
        // notices before the dashboard starts hitting the DB harder than its docs
        // claim.
        verify(userRepository).count();
        verify(userRepository).countBySuspendedAtIsNotNull();
        verify(listingRepository).countByStatus(ListingStatus.LIVE);
        verify(listingRepository).countByStatus(ListingStatus.CLOSED);
        verify(verificationRepository).countByStatus(VerificationStatus.PENDING);
        verify(offerRepository).countByStatus(OfferStatus.PENDING);
        verifyNoMoreInteractions(userRepository, listingRepository, verificationRepository, offerRepository);
    }

    @Test
    void allZeroesForFreshlyDeployedDatabase() {
        when(userRepository.count()).thenReturn(0L);
        when(userRepository.countBySuspendedAtIsNotNull()).thenReturn(0L);
        when(listingRepository.countByStatus(ListingStatus.LIVE)).thenReturn(0L);
        when(listingRepository.countByStatus(ListingStatus.CLOSED)).thenReturn(0L);
        when(verificationRepository.countByStatus(VerificationStatus.PENDING)).thenReturn(0L);
        when(offerRepository.countByStatus(OfferStatus.PENDING)).thenReturn(0L);

        AdminAnalyticsService service = new AdminAnalyticsService(
                userRepository, listingRepository, verificationRepository, offerRepository);

        AnalyticsSummaryResponse summary = service.summary();

        assertThat(summary).isEqualTo(new AnalyticsSummaryResponse(0, 0, 0, 0, 0, 0));
    }
}
