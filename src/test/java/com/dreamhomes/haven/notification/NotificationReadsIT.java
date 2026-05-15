package com.dreamhomes.haven.notification;

import com.dreamhomes.haven.admin.AdminAuditLogRepository;
import com.dreamhomes.haven.agentlisting.AgentListingRepository;
import com.dreamhomes.haven.support.JwtTestSupport;
import com.dreamhomes.haven.comment.CommentRepository;
import com.dreamhomes.haven.support.AbstractPostgresIT;
import com.dreamhomes.haven.listing.model.Listing;
import com.dreamhomes.haven.listing.ListingRepository;
import com.dreamhomes.haven.listing.model.ListingStatus;
import com.dreamhomes.haven.listing.model.ListingType;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end notification read flow:
 * <ol>
 *   <li>Applicant comments on owner's listing → owner gets a sync notification.</li>
 *   <li>Owner pulls /api/notifications/mine — sees the unread row.</li>
 *   <li>Owner pulls /unread-count — gets 1.</li>
 *   <li>Owner marks the row read — readAt stamps.</li>
 *   <li>Owner pulls /unread-count again — gets 0.</li>
 *   <li>Random user pulls owner's notifications — sees nothing of the owner's.</li>
 * </ol>
 */
@AutoConfigureMockMvc
class NotificationReadsIT extends AbstractPostgresIT {

    @Autowired MockMvc mockMvc;
    @Autowired JwtTestSupport jwtTestSupport;
    @Autowired UserRepository userRepository;
    @Autowired AgentProfileRepository agentProfileRepository;
    @Autowired PropertyRepository propertyRepository;
    @Autowired ListingRepository listingRepository;
    @Autowired CommentRepository commentRepository;
    @Autowired NotificationRepository notificationRepository;
    @Autowired VerificationRepository verificationRepository;
    @Autowired AdminAuditLogRepository auditLogRepository;
    @Autowired AgentListingRepository agentListingRepository;


    @Test
    void ownerReadsTheirInboxThenMarksOneReadAndUnreadCountDecrements() throws Exception {
        User owner = jwtTestSupport.persistUser(Role.OWNER);
        User applicant = jwtTestSupport.persistUser(Role.APPLICANT);
        Long listingId = persistLiveListingFor(owner.getId());
        String ownerBearer = jwtTestSupport.bearerFor(owner);

        // Applicant comments → owner gets notified.
        mockMvc.perform(post("/api/listings/" + listingId + "/comments")
                        .header("Authorization", jwtTestSupport.bearerFor(applicant))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"Is this still available?\"}"))
                .andExpect(status().isCreated());

        // Owner sees the inbox.
        mockMvc.perform(get("/api/notifications/mine").header("Authorization", ownerBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].kind").value("COMMENT_POSTED"))
                .andExpect(jsonPath("$.content[0].readAt").value(org.hamcrest.Matchers.nullValue()));

        // Unread count = 1.
        mockMvc.perform(get("/api/notifications/mine/unread-count").header("Authorization", ownerBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unread").value(1));

        Long notificationId = notificationRepository.findAll().get(0).getId();

        // Owner marks read.
        mockMvc.perform(post("/api/notifications/" + notificationId + "/mark-read")
                        .header("Authorization", ownerBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.readAt").exists());

        // Unread count drops.
        mockMvc.perform(get("/api/notifications/mine/unread-count").header("Authorization", ownerBearer))
                .andExpect(jsonPath("$.unread").value(0));

        // unreadOnly=true now returns empty.
        mockMvc.perform(get("/api/notifications/mine?unreadOnly=true")
                        .header("Authorization", ownerBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty());
    }

    @Test
    void otherUserCannotReadOwnersNotifications() throws Exception {
        User owner = jwtTestSupport.persistUser(Role.OWNER);
        User applicant = jwtTestSupport.persistUser(Role.APPLICANT);
        User stranger = jwtTestSupport.persistUser(Role.APPLICANT);
        Long listingId = persistLiveListingFor(owner.getId());

        mockMvc.perform(post("/api/listings/" + listingId + "/comments")
                        .header("Authorization", jwtTestSupport.bearerFor(applicant))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"hi\"}"))
                .andExpect(status().isCreated());

        // Stranger's inbox is empty — every list query is scoped by recipient_user_id.
        mockMvc.perform(get("/api/notifications/mine")
                        .header("Authorization", jwtTestSupport.bearerFor(stranger)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty());

        // And mark-read on someone else's notification is 403.
        Long ownerNotificationId = notificationRepository.findAll().get(0).getId();
        mockMvc.perform(post("/api/notifications/" + ownerNotificationId + "/mark-read")
                        .header("Authorization", jwtTestSupport.bearerFor(stranger)))
                .andExpect(status().isForbidden());
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
