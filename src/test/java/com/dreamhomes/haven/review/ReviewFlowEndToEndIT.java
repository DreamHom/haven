package com.dreamhomes.haven.review;

import com.dreamhomes.haven.admin.AdminAuditLogRepository;
import com.dreamhomes.haven.agentlisting.AgentListingRepository;
import com.dreamhomes.haven.support.JwtTestSupport;
import com.dreamhomes.haven.comment.CommentRepository;
import com.dreamhomes.haven.support.AbstractPostgresIT;
import com.dreamhomes.haven.engagement.ListingSaveRepository;
import com.dreamhomes.haven.listing.Listing;
import com.dreamhomes.haven.listing.ListingRepository;
import com.dreamhomes.haven.listing.ListingStatus;
import com.dreamhomes.haven.listing.ListingType;
import com.dreamhomes.haven.notification.NotificationKind;
import com.dreamhomes.haven.notification.NotificationRepository;
import com.dreamhomes.haven.offer.Offer;
import com.dreamhomes.haven.offer.OfferRepository;
import com.dreamhomes.haven.offer.OfferStatus;
import com.dreamhomes.haven.property.Property;
import com.dreamhomes.haven.property.PropertyRepository;
import com.dreamhomes.haven.property.PropertyType;
import com.dreamhomes.haven.user.AgentProfileRepository;
import com.dreamhomes.haven.user.Role;
import com.dreamhomes.haven.user.User;
import com.dreamhomes.haven.user.UserRepository;
import com.dreamhomes.haven.verification.VerificationRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full review flow:
 * <ol>
 *   <li>Owner has a CLOSED listing with an ACCEPTED offer from an applicant.</li>
 *   <li>Owner reviews the applicant; applicant reviews owner. Both notify the reviewee.</li>
 *   <li>Public profile reads surface average rating + count.</li>
 *   <li>A second review on the same (listing, reviewer, reviewee) is rejected 409.</li>
 *   <li>A non-participant gets 403.</li>
 *   <li>Reviewing on a still-LIVE listing is rejected 409 (listing not closed).</li>
 * </ol>
 */
@AutoConfigureMockMvc
class ReviewFlowEndToEndIT extends AbstractPostgresIT {

    @Autowired MockMvc mockMvc;
    @Autowired JwtTestSupport jwtTestSupport;
    @Autowired UserRepository userRepository;
    @Autowired AgentProfileRepository agentProfileRepository;
    @Autowired PropertyRepository propertyRepository;
    @Autowired ListingRepository listingRepository;
    @Autowired OfferRepository offerRepository;
    @Autowired ListingReviewRepository reviewRepository;
    @Autowired NotificationRepository notificationRepository;
    @Autowired CommentRepository commentRepository;
    @Autowired VerificationRepository verificationRepository;
    @Autowired AdminAuditLogRepository auditLogRepository;
    @Autowired AgentListingRepository agentListingRepository;
    @Autowired ListingSaveRepository listingSaveRepository;

    @BeforeEach
    @AfterEach
    void clean() {
        reviewRepository.deleteAll();
        listingSaveRepository.deleteAll();
        agentListingRepository.deleteAll();
        auditLogRepository.deleteAll();
        commentRepository.deleteAll();
        verificationRepository.deleteAll();
        notificationRepository.deleteAll();
        offerRepository.deleteAll();
        listingRepository.deleteAll();
        propertyRepository.deleteAll();
        agentProfileRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void ownerAndApplicantBothReviewAfterDealCloses() throws Exception {
        User owner = jwtTestSupport.persistUser(Role.OWNER);
        User applicant = jwtTestSupport.persistUser(Role.APPLICANT);
        Long listingId = persistClosedListingWithAcceptedOffer(owner.getId(), applicant.getId());

        // Owner reviews applicant.
        mockMvc.perform(post("/api/listings/" + listingId + "/reviews")
                        .header("Authorization", jwtTestSupport.bearerFor(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "revieweeUserId": %d, "rating": 5, "body": "Smooth deal" }
                                """.formatted(applicant.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.rating").value(5));

        // Applicant gets the sync REVIEW_RECEIVED notification.
        assertThat(notificationRepository.findByRecipientIdOrderByCreatedAtDesc(applicant.getId()))
                .anyMatch(n -> n.getKind() == NotificationKind.REVIEW_RECEIVED);

        // Applicant reviews owner.
        mockMvc.perform(post("/api/listings/" + listingId + "/reviews")
                        .header("Authorization", jwtTestSupport.bearerFor(applicant))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "revieweeUserId": %d, "rating": 4, "body": "Great owner" }
                                """.formatted(owner.getId())))
                .andExpect(status().isCreated());

        // Public profile shows aggregate (anon GET, no JWT).
        mockMvc.perform(get("/api/users/" + owner.getId() + "/profile"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.averageRating").value(4.0))
                .andExpect(jsonPath("$.reviewCount").value(1));

        mockMvc.perform(get("/api/users/" + applicant.getId() + "/profile"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.averageRating").value(5.0))
                .andExpect(jsonPath("$.reviewCount").value(1));

        // Public list of reviews about the owner.
        mockMvc.perform(get("/api/users/" + owner.getId() + "/reviews"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].body").value("Great owner"));
    }

    @Test
    void duplicateReviewIsRejected409() throws Exception {
        User owner = jwtTestSupport.persistUser(Role.OWNER);
        User applicant = jwtTestSupport.persistUser(Role.APPLICANT);
        Long listingId = persistClosedListingWithAcceptedOffer(owner.getId(), applicant.getId());
        String body = """
                { "revieweeUserId": %d, "rating": 5, "body": "x" }
                """.formatted(applicant.getId());

        mockMvc.perform(post("/api/listings/" + listingId + "/reviews")
                        .header("Authorization", jwtTestSupport.bearerFor(owner))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/listings/" + listingId + "/reviews")
                        .header("Authorization", jwtTestSupport.bearerFor(owner))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict());
    }

    @Test
    void reviewingOnStillLiveListingIsRejected409() throws Exception {
        User owner = jwtTestSupport.persistUser(Role.OWNER);
        User applicant = jwtTestSupport.persistUser(Role.APPLICANT);
        Property property = persistProperty(owner.getId());
        Listing live = listingRepository.save(Listing.builder()
                .propertyId(property.getId()).ownerId(owner.getId())
                .listingType(ListingType.SALE).askingPrice(new BigDecimal("80000000.00")).currency("NGN")
                .status(ListingStatus.LIVE)
                .createdAt(Instant.now()).updatedAt(Instant.now()).build());
        // Even if there's an ACCEPTED offer, the listing is still LIVE — reviews don't open.
        offerRepository.save(Offer.builder()
                .listingId(live.getId()).applicantId(applicant.getId()).ownerId(owner.getId())
                .proposedByUserId(applicant.getId())
                .amount(new BigDecimal("80000000.00")).currency("NGN")
                .status(OfferStatus.ACCEPTED)
                .createdAt(Instant.now()).updatedAt(Instant.now()).build());

        mockMvc.perform(post("/api/listings/" + live.getId() + "/reviews")
                        .header("Authorization", jwtTestSupport.bearerFor(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "revieweeUserId": %d, "rating": 5, "body": "x" }
                                """.formatted(applicant.getId())))
                .andExpect(status().isConflict());
    }

    @Test
    void nonParticipantCannotReview() throws Exception {
        User owner = jwtTestSupport.persistUser(Role.OWNER);
        User applicant = jwtTestSupport.persistUser(Role.APPLICANT);
        User stranger = jwtTestSupport.persistUser(Role.APPLICANT);
        Long listingId = persistClosedListingWithAcceptedOffer(owner.getId(), applicant.getId());

        mockMvc.perform(post("/api/listings/" + listingId + "/reviews")
                        .header("Authorization", jwtTestSupport.bearerFor(stranger))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "revieweeUserId": %d, "rating": 5, "body": "x" }
                                """.formatted(owner.getId())))
                .andExpect(status().isForbidden());
    }

    @Test
    void anonymousCanReadReviewsButNotPostThem() throws Exception {
        User owner = jwtTestSupport.persistUser(Role.OWNER);
        User applicant = jwtTestSupport.persistUser(Role.APPLICANT);
        Long listingId = persistClosedListingWithAcceptedOffer(owner.getId(), applicant.getId());

        // Public reads work without auth.
        mockMvc.perform(get("/api/listings/" + listingId + "/reviews"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/users/" + owner.getId() + "/reviews"))
                .andExpect(status().isOk());

        // POST requires auth.
        mockMvc.perform(post("/api/listings/" + listingId + "/reviews")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "revieweeUserId": %d, "rating": 5, "body": "x" }
                                """.formatted(applicant.getId())))
                .andExpect(status().isUnauthorized());
    }

    private Long persistClosedListingWithAcceptedOffer(Long ownerId, Long applicantId) {
        Property property = persistProperty(ownerId);
        Listing listing = listingRepository.save(Listing.builder()
                .propertyId(property.getId()).ownerId(ownerId)
                .listingType(ListingType.SALE).askingPrice(new BigDecimal("80000000.00")).currency("NGN")
                .status(ListingStatus.CLOSED)
                .createdAt(Instant.now()).updatedAt(Instant.now()).build());
        offerRepository.save(Offer.builder()
                .listingId(listing.getId()).applicantId(applicantId).ownerId(ownerId)
                .proposedByUserId(applicantId)
                .amount(new BigDecimal("75000000.00")).currency("NGN")
                .status(OfferStatus.ACCEPTED)
                .createdAt(Instant.now()).updatedAt(Instant.now()).build());
        return listing.getId();
    }

    private Property persistProperty(Long ownerId) {
        return propertyRepository.save(Property.builder()
                .ownerId(ownerId).type(PropertyType.HOUSE)
                .address("addr").bedrooms(3).bathrooms(2)
                .createdAt(Instant.now()).build());
    }
}
