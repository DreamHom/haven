package com.dreamhomes.haven.review;

import com.dreamhomes.haven.admin.AdminAuditLogRepository;
import com.dreamhomes.haven.agentlisting.AgentListingRepository;
import com.dreamhomes.haven.agentlisting.model.AgentListing;
import com.dreamhomes.haven.agentlisting.model.AgentListingStatus;
import com.dreamhomes.haven.support.JwtTestSupport;
import com.dreamhomes.haven.comment.CommentRepository;
import com.dreamhomes.haven.support.AbstractPostgresIT;
import com.dreamhomes.haven.engagement.ListingSaveRepository;
import com.dreamhomes.haven.listing.model.Listing;
import com.dreamhomes.haven.listing.ListingRepository;
import com.dreamhomes.haven.listing.model.ListingStatus;
import com.dreamhomes.haven.listing.model.ListingType;
import com.dreamhomes.haven.notification.model.NotificationKind;
import com.dreamhomes.haven.notification.NotificationRepository;
import com.dreamhomes.haven.offer.model.Offer;
import com.dreamhomes.haven.offer.OfferRepository;
import com.dreamhomes.haven.offer.model.OfferStatus;
import com.dreamhomes.haven.property.model.Property;
import com.dreamhomes.haven.property.PropertyRepository;
import com.dreamhomes.haven.property.model.PropertyType;
import com.dreamhomes.haven.user.repository.AgentProfileRepository;
import com.dreamhomes.haven.user.model.Role;
import com.dreamhomes.haven.user.model.User;
import com.dreamhomes.haven.user.repository.UserRepository;
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

    // ============================ Items 9 + 11: eligibility + agent reviews ============================

    @Test
    void eligibilityReturnsBothFalseForUnrelatedApplicant() throws Exception {
        User owner = jwtTestSupport.persistUser(Role.OWNER);
        User applicant = jwtTestSupport.persistUser(Role.APPLICANT);
        User outsider = jwtTestSupport.persistUser(Role.APPLICANT);
        Long listingId = persistClosedListingWithAcceptedOffer(owner.getId(), applicant.getId());

        mockMvc.perform(get("/api/listings/" + listingId + "/reviews/me/eligibility")
                        .header("Authorization", jwtTestSupport.bearerFor(outsider)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.listingStatus").value("CLOSED"))
                .andExpect(jsonPath("$.canReviewOwner").value(false))
                .andExpect(jsonPath("$.canReviewAgent").value(false))
                .andExpect(jsonPath("$.ownerUserId").value(owner.getId().intValue()))
                // No agent on this listing → agentUserId is null in the JSON body.
                .andExpect(jsonPath("$.reasons.owner").isNotEmpty());
    }

    @Test
    void eligibilityShowsCanReviewBothWhenAgentAcceptedAndApplicantWon() throws Exception {
        User owner = jwtTestSupport.persistUser(Role.OWNER);
        User applicant = jwtTestSupport.persistUser(Role.APPLICANT);
        User agent = jwtTestSupport.persistUser(Role.AGENT);
        Long listingId = persistClosedListingWithAcceptedOffer(owner.getId(), applicant.getId());
        persistAcceptedAgentListing(listingId, agent.getId(), owner.getId());

        mockMvc.perform(get("/api/listings/" + listingId + "/reviews/me/eligibility")
                        .header("Authorization", jwtTestSupport.bearerFor(applicant)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canReviewOwner").value(true))
                .andExpect(jsonPath("$.canReviewAgent").value(true))
                .andExpect(jsonPath("$.agentUserId").value(agent.getId().intValue()));
    }

    @Test
    void eligibilityEndpointRequiresAuth() throws Exception {
        User owner = jwtTestSupport.persistUser(Role.OWNER);
        User applicant = jwtTestSupport.persistUser(Role.APPLICANT);
        Long listingId = persistClosedListingWithAcceptedOffer(owner.getId(), applicant.getId());

        mockMvc.perform(get("/api/listings/" + listingId + "/reviews/me/eligibility"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void applicantCanReviewBothOwnerAndAcceptedAgentOnClosedListing() throws Exception {
        User owner = jwtTestSupport.persistUser(Role.OWNER);
        User applicant = jwtTestSupport.persistUser(Role.APPLICANT);
        User agent = jwtTestSupport.persistUser(Role.AGENT);
        Long listingId = persistClosedListingWithAcceptedOffer(owner.getId(), applicant.getId());
        persistAcceptedAgentListing(listingId, agent.getId(), owner.getId());

        // Owner review (existing behaviour).
        mockMvc.perform(post("/api/listings/" + listingId + "/reviews")
                        .header("Authorization", jwtTestSupport.bearerFor(applicant))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "revieweeUserId": %d, "rating": 4, "body": "Great owner" }
                                """.formatted(owner.getId())))
                .andExpect(status().isCreated());

        // NEW: agent review (Item 11).
        mockMvc.perform(post("/api/listings/" + listingId + "/reviews")
                        .header("Authorization", jwtTestSupport.bearerFor(applicant))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "revieweeUserId": %d, "rating": 5, "body": "Agent did all the work" }
                                """.formatted(agent.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.revieweeUserId").value(agent.getId().intValue()));

        // Aggregate updated for the agent.
        mockMvc.perform(get("/api/users/" + agent.getId() + "/profile"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.averageRating").value(5.0))
                .andExpect(jsonPath("$.reviewCount").value(1));
    }

    @Test
    void applicantCannotReviewAgentWhoseAssignmentWasRevoked() throws Exception {
        User owner = jwtTestSupport.persistUser(Role.OWNER);
        User applicant = jwtTestSupport.persistUser(Role.APPLICANT);
        User agent = jwtTestSupport.persistUser(Role.AGENT);
        Long listingId = persistClosedListingWithAcceptedOffer(owner.getId(), applicant.getId());
        AgentListing al = persistAcceptedAgentListing(listingId, agent.getId(), owner.getId());
        al.setStatus(AgentListingStatus.REVOKED);
        agentListingRepository.save(al);

        mockMvc.perform(post("/api/listings/" + listingId + "/reviews")
                        .header("Authorization", jwtTestSupport.bearerFor(applicant))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "revieweeUserId": %d, "rating": 5, "body": "x" }
                                """.formatted(agent.getId())))
                .andExpect(status().isForbidden());
    }

    private AgentListing persistAcceptedAgentListing(Long listingId, Long agentId, Long ownerId) {
        Instant now = Instant.now();
        return agentListingRepository.save(AgentListing.builder()
                .listingId(listingId).agentUserId(agentId).requestedByOwnerId(ownerId)
                .status(AgentListingStatus.ACCEPTED)
                .requestedAt(now).decidedAt(now)
                .build());
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
