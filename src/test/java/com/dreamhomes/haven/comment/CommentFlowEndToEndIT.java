package com.dreamhomes.haven.comment;

import com.dreamhomes.haven.admin.AdminAuditLogRepository;
import com.dreamhomes.haven.support.JwtTestSupport;
import com.dreamhomes.haven.support.AbstractPostgresIT;
import com.dreamhomes.haven.listing.model.Listing;
import com.dreamhomes.haven.listing.ListingRepository;
import com.dreamhomes.haven.listing.model.ListingStatus;
import com.dreamhomes.haven.listing.model.ListingType;
import com.dreamhomes.haven.notification.model.Notification;
import com.dreamhomes.haven.notification.model.NotificationKind;
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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Comment flow end-to-end:
 * <ol>
 *   <li>Public visitor lists comments on a listing (empty).</li>
 *   <li>Applicant posts a comment → owner gets a sync notification.</li>
 *   <li>Owner self-posts → no extra notification.</li>
 *   <li>Listing owner deletes the applicant's comment with a reason → public list returns empty.</li>
 *   <li>Random user trying to delete a different applicant's comment is forbidden.</li>
 * </ol>
 */
@AutoConfigureMockMvc
class CommentFlowEndToEndIT extends AbstractPostgresIT {

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

    @BeforeEach
    @AfterEach
    void clean() {
        // FK order: audit + comments + verification + notifications reference users/listings.
        auditLogRepository.deleteAll();
        commentRepository.deleteAll();
        verificationRepository.deleteAll();
        notificationRepository.deleteAll();
        listingRepository.deleteAll();
        propertyRepository.deleteAll();
        agentProfileRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void applicantPostsOwnerNotifiedOwnerDeletesPublicListEmpty() throws Exception {
        User owner = jwtTestSupport.persistUser(Role.OWNER);
        User applicant = jwtTestSupport.persistUser(Role.APPLICANT);
        Long listingId = persistLiveListingFor(owner.getId());

        // 1. Public visitor sees empty comment list.
        mockMvc.perform(get("/api/listings/" + listingId + "/comments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty());

        // 2. Applicant posts.
        mockMvc.perform(post("/api/listings/" + listingId + "/comments")
                        .header("Authorization", jwtTestSupport.bearerFor(applicant))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"Is this still available?\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.body").value("Is this still available?"));
        Long commentId = commentRepository.findAll().get(0).getId();

        // 3. Owner gets a sync notification (no Kafka — PRD §7).
        List<Notification> ownerNotifs = notificationRepository
                .findByRecipientIdOrderByCreatedAtDesc(owner.getId());
        assertThat(ownerNotifs).hasSize(1);
        assertThat(ownerNotifs.get(0).getKind()).isEqualTo(NotificationKind.COMMENT_POSTED);
        assertThat(ownerNotifs.get(0).getPayload())
                .contains("\"commentId\":" + commentId)
                .contains("\"authorUserId\":" + applicant.getId());

        // 4. Public visitor now sees the comment.
        mockMvc.perform(get("/api/listings/" + listingId + "/comments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(commentId));

        // 5. Owner takes the comment down with a reason.
        mockMvc.perform(delete("/api/comments/" + commentId)
                        .header("Authorization", jwtTestSupport.bearerFor(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Off-topic\"}"))
                .andExpect(status().isNoContent());

        // 6. Soft-deleted: row still exists but public list is empty.
        Comment row = commentRepository.findById(commentId).orElseThrow();
        assertThat(row.isDeleted()).isTrue();
        assertThat(row.getDeletedByUserId()).isEqualTo(owner.getId());
        assertThat(row.getDeletionReason()).isEqualTo("Off-topic");

        mockMvc.perform(get("/api/listings/" + listingId + "/comments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty());
    }

    @Test
    void ownerSelfCommentDoesNotSelfNotify() throws Exception {
        User owner = jwtTestSupport.persistUser(Role.OWNER);
        Long listingId = persistLiveListingFor(owner.getId());

        mockMvc.perform(post("/api/listings/" + listingId + "/comments")
                        .header("Authorization", jwtTestSupport.bearerFor(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"Anything I should clarify?\"}"))
                .andExpect(status().isCreated());

        assertThat(notificationRepository.findByRecipientIdOrderByCreatedAtDesc(owner.getId())).isEmpty();
    }

    @Test
    void unrelatedApplicantCannotDeleteAnotherApplicantsComment() throws Exception {
        User owner = jwtTestSupport.persistUser(Role.OWNER);
        User applicantA = jwtTestSupport.persistUser(Role.APPLICANT);
        User applicantB = jwtTestSupport.persistUser(Role.APPLICANT);
        Long listingId = persistLiveListingFor(owner.getId());

        mockMvc.perform(post("/api/listings/" + listingId + "/comments")
                        .header("Authorization", jwtTestSupport.bearerFor(applicantA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"hi\"}"))
                .andExpect(status().isCreated());
        Long commentId = commentRepository.findAll().get(0).getId();

        mockMvc.perform(delete("/api/comments/" + commentId)
                        .header("Authorization", jwtTestSupport.bearerFor(applicantB)))
                .andExpect(status().isForbidden());

        assertThat(commentRepository.findById(commentId).orElseThrow().isDeleted()).isFalse();
    }

    @Test
    void adminCanDeleteAnyComment() throws Exception {
        User owner = jwtTestSupport.persistUser(Role.OWNER);
        User applicant = jwtTestSupport.persistUser(Role.APPLICANT);
        User admin = jwtTestSupport.persistUser(Role.ADMIN);
        Long listingId = persistLiveListingFor(owner.getId());

        mockMvc.perform(post("/api/listings/" + listingId + "/comments")
                        .header("Authorization", jwtTestSupport.bearerFor(applicant))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"spam content\"}"))
                .andExpect(status().isCreated());
        Long commentId = commentRepository.findAll().get(0).getId();

        mockMvc.perform(delete("/api/comments/" + commentId)
                        .header("Authorization", jwtTestSupport.bearerFor(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"spam\"}"))
                .andExpect(status().isNoContent());

        assertThat(commentRepository.findById(commentId).orElseThrow().isDeleted()).isTrue();
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
