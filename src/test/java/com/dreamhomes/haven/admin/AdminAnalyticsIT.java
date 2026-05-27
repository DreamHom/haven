package com.dreamhomes.haven.admin;

import com.dreamhomes.haven.listing.ListingRepository;
import com.dreamhomes.haven.listing.model.Listing;
import com.dreamhomes.haven.listing.model.ListingStatus;
import com.dreamhomes.haven.listing.model.ListingType;
import com.dreamhomes.haven.notification.NotificationRepository;
import com.dreamhomes.haven.offer.OfferRepository;
import com.dreamhomes.haven.offer.model.Offer;
import com.dreamhomes.haven.offer.model.OfferStatus;
import com.dreamhomes.haven.property.PropertyRepository;
import com.dreamhomes.haven.property.model.Property;
import com.dreamhomes.haven.property.model.PropertyType;
import com.dreamhomes.haven.support.AbstractPostgresIT;
import com.dreamhomes.haven.support.JwtTestSupport;
import com.dreamhomes.haven.user.model.Role;
import com.dreamhomes.haven.user.model.User;
import com.dreamhomes.haven.user.repository.AgentProfileRepository;
import com.dreamhomes.haven.user.repository.UserRepository;
import com.dreamhomes.haven.verification.VerificationRepository;
import com.dreamhomes.haven.verification.model.Verification;
import com.dreamhomes.haven.verification.model.VerificationStatus;
import com.dreamhomes.haven.verification.model.VerificationType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end check that the analytics summary reflects the actual seeded shape of the
 * database — every aggregate is the result of a real query against real rows, not a
 * pretend constant. We seed a small known mix and assert the response.
 *
 * <p>Also confirms the role gate — a non-admin caller hitting the endpoint gets 403
 * from {@code @PreAuthorize}, not a successful read of platform-wide counts.</p>
 */
@AutoConfigureMockMvc
class AdminAnalyticsIT extends AbstractPostgresIT {

    @Autowired MockMvc mockMvc;
    @Autowired JwtTestSupport jwtTestSupport;
    @Autowired UserRepository userRepository;
    @Autowired AgentProfileRepository agentProfileRepository;
    @Autowired PropertyRepository propertyRepository;
    @Autowired ListingRepository listingRepository;
    @Autowired VerificationRepository verificationRepository;
    @Autowired OfferRepository offerRepository;
    @Autowired NotificationRepository notificationRepository;
    @Autowired AdminAuditLogRepository auditLogRepository;


    @Test
    void summaryReflectsSeededDatabaseShape() throws Exception {
        // Wipe the V11-seeded platform admin so user counts are deterministic when this
        // class runs first in the JVM (no other IT's cleanup has truncated it yet).
        userRepository.deleteAll();

        // Seed: 3 users (1 admin + 2 owners), suspend one owner.
        User admin = jwtTestSupport.persistUser(Role.ADMIN);
        User ownerA = jwtTestSupport.persistUser(Role.OWNER);
        User ownerB = jwtTestSupport.persistUser(Role.OWNER);
        ownerB.setSuspendedAt(Instant.now());
        userRepository.save(ownerB);

        // 3 listings: 2 LIVE + 1 CLOSED. V47 enforces one LIVE listing per
        // (property, listing_type) — so each LIVE listing needs its own property.
        persistListing(persistPropertyFor(ownerA.getId()), ownerA.getId(), ListingStatus.LIVE);
        persistListing(persistPropertyFor(ownerA.getId()), ownerA.getId(), ListingStatus.LIVE);
        Listing closed = persistListing(persistPropertyFor(ownerA.getId()), ownerA.getId(), ListingStatus.CLOSED);

        // 2 verifications: 1 PENDING + 1 APPROVED. Only PENDING shows in the summary.
        verificationRepository.save(verification(ownerA.getId(), VerificationStatus.PENDING, admin.getId()));
        verificationRepository.save(verification(ownerA.getId(), VerificationStatus.APPROVED, admin.getId()));

        // 4 offers: 3 PENDING (in flight) + 1 ACCEPTED.
        offerRepository.save(offer(closed.getId(), ownerA.getId(), ownerB.getId(), OfferStatus.PENDING));
        offerRepository.save(offer(closed.getId(), ownerA.getId(), ownerB.getId(), OfferStatus.PENDING));
        offerRepository.save(offer(closed.getId(), ownerA.getId(), ownerB.getId(), OfferStatus.PENDING));
        offerRepository.save(offer(closed.getId(), ownerA.getId(), ownerB.getId(), OfferStatus.ACCEPTED));

        mockMvc.perform(get("/api/admin/analytics/summary")
                        .header("Authorization", jwtTestSupport.bearerFor(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalUsers").value(3))
                .andExpect(jsonPath("$.suspendedUsers").value(1))
                .andExpect(jsonPath("$.openListings").value(2))
                .andExpect(jsonPath("$.closedListings").value(1))
                .andExpect(jsonPath("$.pendingVerifications").value(1))
                .andExpect(jsonPath("$.pendingOffers").value(3));
    }

    @Test
    void nonAdminCallersAreForbidden() throws Exception {
        User owner = jwtTestSupport.persistUser(Role.OWNER);

        mockMvc.perform(get("/api/admin/analytics/summary")
                        .header("Authorization", jwtTestSupport.bearerFor(owner)))
                .andExpect(status().isForbidden());
    }

    @Test
    void anonymousCallersAreUnauthorized() throws Exception {
        mockMvc.perform(get("/api/admin/analytics/summary"))
                .andExpect(status().isUnauthorized());
    }

    // --- seed helpers ---

    private Long persistPropertyFor(Long ownerId) {
        Property p = propertyRepository.save(Property.builder()
                .ownerId(ownerId).type(PropertyType.HOUSE)
                .address("seed").bedrooms(2).bathrooms(1)
                .createdAt(Instant.now()).build());
        return p.getId();
    }

    private Listing persistListing(Long propertyId, Long ownerId, ListingStatus status) {
        return listingRepository.save(Listing.builder()
                .propertyId(propertyId).ownerId(ownerId)
                .listingType(ListingType.RENT).status(status)
                .askingPrice(new BigDecimal("100000")).currency("NGN")
                .createdAt(Instant.now()).updatedAt(Instant.now())
                .build());
    }

    private Verification verification(Long targetUserId, VerificationStatus status, Long adminId) {
        // The `verifications_decision_complete` check constraint (V10) requires that
        // every non-PENDING row has decidedAt + decidedByAdminId populated. Honour it
        // here so seeding APPROVED/REJECTED rows from tests doesn't trip the DB.
        Verification.VerificationBuilder b = Verification.builder()
                .type(VerificationType.OWNER_IDENTITY)
                .submitterUserId(targetUserId)
                .targetUserId(targetUserId)
                .status(status)
                .documentRefs("{\"kind\":\"NIN\",\"ref\":\"seed\"}")
                .submittedAt(Instant.now());
        if (status != VerificationStatus.PENDING) {
            b.decidedAt(Instant.now()).decidedByAdminId(adminId);
        }
        return b.build();
    }

    private Offer offer(Long listingId, Long ownerId, Long applicantId, OfferStatus status) {
        return Offer.builder()
                .listingId(listingId).ownerId(ownerId).applicantId(applicantId)
                .amount(new BigDecimal("100000")).currency("NGN")
                .status(status).proposedByUserId(applicantId)
                .createdAt(Instant.now()).updatedAt(Instant.now())
                .build();
    }
}
