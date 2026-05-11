package com.dreamhomes.haven.admin.service;

import com.dreamhomes.haven.admin.dto.AnalyticsSummaryResponse;
import com.dreamhomes.haven.listing.ListingRepository;
import com.dreamhomes.haven.listing.model.ListingStatus;
import com.dreamhomes.haven.offer.OfferRepository;
import com.dreamhomes.haven.offer.model.OfferStatus;
import com.dreamhomes.haven.user.repository.UserRepository;
import com.dreamhomes.haven.verification.VerificationRepository;
import com.dreamhomes.haven.verification.model.VerificationStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Computes the admin analytics summary by issuing one count query per field.
 *
 * <p>Six lookups, none of which scan more than a single status-filtered index, so the
 * total cost is O(1) regardless of table size — verified against the relevant indexes
 * (`idx_listings_status` for the listings split, the `verifications` partial unique
 * index for the pending count). Keep an eye on this if more aggregates are added; at
 * ~10 fields it's still cheap, at ~50 we'd want to batch into a single SQL.</p>
 */
@Service
@RequiredArgsConstructor
public class AdminAnalyticsService {

    private final UserRepository userRepository;
    private final ListingRepository listingRepository;
    private final VerificationRepository verificationRepository;
    private final OfferRepository offerRepository;

    @Transactional(readOnly = true)
    public AnalyticsSummaryResponse summary() {
        return new AnalyticsSummaryResponse(
                userRepository.count(),
                userRepository.countBySuspendedAtIsNotNull(),
                listingRepository.countByStatus(ListingStatus.LIVE),
                listingRepository.countByStatus(ListingStatus.CLOSED),
                verificationRepository.countByStatus(VerificationStatus.PENDING),
                offerRepository.countByStatus(OfferStatus.PENDING)
        );
    }
}
