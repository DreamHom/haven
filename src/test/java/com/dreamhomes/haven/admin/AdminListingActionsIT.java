package com.dreamhomes.haven.admin;

import com.dreamhomes.haven.support.JwtTestSupport;
import com.dreamhomes.haven.support.AbstractPostgresIT;
import com.dreamhomes.haven.listing.Listing;
import com.dreamhomes.haven.listing.ListingRepository;
import com.dreamhomes.haven.listing.ListingStatus;
import com.dreamhomes.haven.listing.ListingType;
import com.dreamhomes.haven.notification.Notification;
import com.dreamhomes.haven.notification.NotificationKind;
import com.dreamhomes.haven.notification.NotificationRepository;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class AdminListingActionsIT extends AbstractPostgresIT {

    @Autowired MockMvc mockMvc;
    @Autowired JwtTestSupport jwtTestSupport;
    @Autowired UserRepository userRepository;
    @Autowired AgentProfileRepository agentProfileRepository;
    @Autowired PropertyRepository propertyRepository;
    @Autowired ListingRepository listingRepository;
    @Autowired NotificationRepository notificationRepository;
    @Autowired VerificationRepository verificationRepository;
    @Autowired AdminAuditLogRepository auditLogRepository;

    @BeforeEach
    @AfterEach
    void clean() {
        auditLogRepository.deleteAll();
        notificationRepository.deleteAll();
        verificationRepository.deleteAll();
        listingRepository.deleteAll();
        propertyRepository.deleteAll();
        agentProfileRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void adminApprovesListingStampsApprovedAtAndOwnerSeesNotification() throws Exception {
        User owner = jwtTestSupport.persistUser(Role.OWNER);
        User admin = jwtTestSupport.persistUser(Role.ADMIN);
        Long listingId = persistLiveListingFor(owner.getId());

        mockMvc.perform(post("/api/admin/listings/" + listingId + "/approve")
                        .header("Authorization", jwtTestSupport.bearerFor(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.approvedAt").exists());

        Listing approved = listingRepository.findById(listingId).orElseThrow();
        assertThat(approved.getApprovedAt()).isNotNull();
        assertThat(approved.getStatus()).isEqualTo(ListingStatus.LIVE); // approval doesn't change status

        List<Notification> ownerNotifs = notificationRepository
                .findByRecipientIdOrderByCreatedAtDesc(owner.getId());
        assertThat(ownerNotifs).hasSize(1);
        assertThat(ownerNotifs.get(0).getKind()).isEqualTo(NotificationKind.LISTING_APPROVED);
        assertThat(auditLogRepository.findAll()).hasSize(1);
    }

    @Test
    void adminTakedownTransitionsListingToClosedAndOwnerSeesReason() throws Exception {
        User owner = jwtTestSupport.persistUser(Role.OWNER);
        User admin = jwtTestSupport.persistUser(Role.ADMIN);
        Long listingId = persistLiveListingFor(owner.getId());

        mockMvc.perform(post("/api/admin/listings/" + listingId + "/takedown")
                        .header("Authorization", jwtTestSupport.bearerFor(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "reason": "Reported as fraudulent" }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"));

        assertThat(listingRepository.findById(listingId).orElseThrow().getStatus())
                .isEqualTo(ListingStatus.CLOSED);

        Notification ownerNotif = notificationRepository
                .findByRecipientIdOrderByCreatedAtDesc(owner.getId()).get(0);
        assertThat(ownerNotif.getKind()).isEqualTo(NotificationKind.LISTING_TAKEDOWN);
        assertThat(ownerNotif.getPayload()).contains("Reported as fraudulent");
    }

    @Test
    void doubleApproveOnSameListingReturns409Conflict() throws Exception {
        User owner = jwtTestSupport.persistUser(Role.OWNER);
        User admin = jwtTestSupport.persistUser(Role.ADMIN);
        Long listingId = persistLiveListingFor(owner.getId());

        mockMvc.perform(post("/api/admin/listings/" + listingId + "/approve")
                        .header("Authorization", jwtTestSupport.bearerFor(admin)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/admin/listings/" + listingId + "/approve")
                        .header("Authorization", jwtTestSupport.bearerFor(admin)))
                .andExpect(status().isConflict());
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
