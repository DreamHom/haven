package com.dreamhomes.haven.engagement;

import com.dreamhomes.haven.admin.AdminAuditLogRepository;
import com.dreamhomes.haven.agentlisting.AgentListingRepository;
import com.dreamhomes.haven.support.JwtTestSupport;
import com.dreamhomes.haven.comment.CommentRepository;
import com.dreamhomes.haven.support.AbstractPostgresIT;
import com.dreamhomes.haven.listing.model.Listing;
import com.dreamhomes.haven.listing.ListingRepository;
import com.dreamhomes.haven.listing.model.ListingStatus;
import com.dreamhomes.haven.listing.model.ListingType;
import com.dreamhomes.haven.notification.NotificationRepository;
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
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Engagement: save / unsave + view_count atomic increment on the public detail GET.
 */
@AutoConfigureMockMvc
class EngagementFlowEndToEndIT extends AbstractPostgresIT {

    @Autowired MockMvc mockMvc;
    @Autowired JwtTestSupport jwtTestSupport;
    @Autowired UserRepository userRepository;
    @Autowired AgentProfileRepository agentProfileRepository;
    @Autowired PropertyRepository propertyRepository;
    @Autowired ListingRepository listingRepository;
    @Autowired ListingSaveRepository listingSaveRepository;
    @Autowired NotificationRepository notificationRepository;
    @Autowired CommentRepository commentRepository;
    @Autowired VerificationRepository verificationRepository;
    @Autowired AdminAuditLogRepository auditLogRepository;
    @Autowired AgentListingRepository agentListingRepository;


    @Test
    void applicantSavesAndUnsavesListingIdempotently() throws Exception {
        User owner = jwtTestSupport.persistUser(Role.OWNER);
        User applicant = jwtTestSupport.persistUser(Role.APPLICANT);
        Long listingId = persistLiveListingFor(owner.getId());
        String bearer = jwtTestSupport.bearerFor(applicant);

        mockMvc.perform(post("/api/listings/" + listingId + "/save")
                        .header("Authorization", bearer))
                .andExpect(status().isNoContent());

        // Re-saving is idempotent.
        mockMvc.perform(post("/api/listings/" + listingId + "/save")
                        .header("Authorization", bearer))
                .andExpect(status().isNoContent());

        assertThat(listingSaveRepository.countByListingId(listingId)).isEqualTo(1L);

        mockMvc.perform(get("/api/saves/mine").header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].listingId").value(listingId));

        // Unsave + idempotent re-unsave.
        mockMvc.perform(delete("/api/listings/" + listingId + "/save")
                        .header("Authorization", bearer))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/listings/" + listingId + "/save")
                        .header("Authorization", bearer))
                .andExpect(status().isNoContent());

        assertThat(listingSaveRepository.countByListingId(listingId)).isZero();
    }

    @Test
    void publicListingDetailIncrementsViewCount() throws Exception {
        User owner = jwtTestSupport.persistUser(Role.OWNER);
        Long listingId = persistLiveListingFor(owner.getId());

        // Three anonymous detail GETs.
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(get("/api/listings/" + listingId))
                    .andExpect(status().isOk());
        }

        // Atomic SQL UPDATEs landed.
        Listing reloaded = listingRepository.findById(listingId).orElseThrow();
        assertThat(reloaded.getViewCount()).isEqualTo(3L);
    }

    @Test
    void anonymousCannotSaveOrUnsave() throws Exception {
        User owner = jwtTestSupport.persistUser(Role.OWNER);
        Long listingId = persistLiveListingFor(owner.getId());

        mockMvc.perform(post("/api/listings/" + listingId + "/save"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/listings/" + listingId + "/save"))
                .andExpect(status().isUnauthorized());
    }

    private Long persistLiveListingFor(Long ownerId) {
        Property property = propertyRepository.save(Property.builder()
                .ownerId(ownerId).type(PropertyType.HOUSE)
                .address("addr").bedrooms(3).bathrooms(2)
                .createdAt(Instant.now()).build());
        Listing listing = listingRepository.save(Listing.builder()
                .propertyId(property.getId()).ownerId(ownerId)
                .listingType(ListingType.SALE).askingPrice(new BigDecimal("80000000.00")).currency("NGN")
                .status(ListingStatus.LIVE)
                .createdAt(Instant.now()).updatedAt(Instant.now()).build());
        return listing.getId();
    }
}
