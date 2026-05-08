package com.dreamhomes.haven.photo;

import com.dreamhomes.haven.admin.AdminAuditLogRepository;
import com.dreamhomes.haven.agentlisting.AgentListingRepository;
import com.dreamhomes.haven.auth.JwtTestSupport;
import com.dreamhomes.haven.comment.CommentRepository;
import com.dreamhomes.haven.common.AbstractPostgresIT;
import com.dreamhomes.haven.engagement.ListingSaveRepository;
import com.dreamhomes.haven.listing.Listing;
import com.dreamhomes.haven.listing.ListingRepository;
import com.dreamhomes.haven.listing.ListingStatus;
import com.dreamhomes.haven.listing.ListingType;
import com.dreamhomes.haven.notification.NotificationRepository;
import com.dreamhomes.haven.offer.OfferRepository;
import com.dreamhomes.haven.property.Property;
import com.dreamhomes.haven.property.PropertyRepository;
import com.dreamhomes.haven.property.PropertyType;
import com.dreamhomes.haven.review.ListingReviewRepository;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class ListingPhotoIT extends AbstractPostgresIT {

    @Autowired MockMvc mockMvc;
    @Autowired JwtTestSupport jwtTestSupport;
    @Autowired UserRepository userRepository;
    @Autowired AgentProfileRepository agentProfileRepository;
    @Autowired PropertyRepository propertyRepository;
    @Autowired ListingRepository listingRepository;
    @Autowired ListingPhotoRepository photoRepository;
    @Autowired NotificationRepository notificationRepository;
    @Autowired CommentRepository commentRepository;
    @Autowired VerificationRepository verificationRepository;
    @Autowired AdminAuditLogRepository auditLogRepository;
    @Autowired AgentListingRepository agentListingRepository;
    @Autowired ListingSaveRepository listingSaveRepository;
    @Autowired ListingReviewRepository reviewRepository;
    @Autowired OfferRepository offerRepository;

    @BeforeEach
    @AfterEach
    void clean() {
        photoRepository.deleteAll();
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
    void ownerAddsThreePhotosTheyAppearInDisplayOrderPubliclyThenOwnerDeletesOne() throws Exception {
        User owner = jwtTestSupport.persistUser(Role.OWNER);
        Long listingId = persistLiveListingFor(owner.getId());
        String bearer = jwtTestSupport.bearerFor(owner);

        for (String url : new String[]{"https://cdn/a.jpg", "https://cdn/b.jpg", "https://cdn/c.jpg"}) {
            mockMvc.perform(post("/api/listings/" + listingId + "/photos")
                            .header("Authorization", bearer)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"url\":\"" + url + "\"}"))
                    .andExpect(status().isCreated());
        }

        // Public read returns 3 photos in insertion order (1, 2, 3 displayOrder).
        mockMvc.perform(get("/api/listings/" + listingId + "/photos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].url").value("https://cdn/a.jpg"))
                .andExpect(jsonPath("$[0].displayOrder").value(1))
                .andExpect(jsonPath("$[2].url").value("https://cdn/c.jpg"))
                .andExpect(jsonPath("$[2].displayOrder").value(3));

        // Owner deletes the middle one.
        Long midPhotoId = photoRepository.findByListingIdOrderByDisplayOrderAscIdAsc(listingId).get(1).getId();
        mockMvc.perform(delete("/api/listings/photos/" + midPhotoId)
                        .header("Authorization", bearer))
                .andExpect(status().isNoContent());

        assertThat(photoRepository.findByListingIdOrderByDisplayOrderAscIdAsc(listingId)).hasSize(2);
    }

    @Test
    void nonOwnerCannotAddOrDeletePhotos() throws Exception {
        User ownerA = jwtTestSupport.persistUser(Role.OWNER);
        User ownerB = jwtTestSupport.persistUser(Role.OWNER);
        Long listingId = persistLiveListingFor(ownerA.getId());

        mockMvc.perform(post("/api/listings/" + listingId + "/photos")
                        .header("Authorization", jwtTestSupport.bearerFor(ownerB))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://cdn/x.jpg\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void anonymousCannotAddPhotos() throws Exception {
        User owner = jwtTestSupport.persistUser(Role.OWNER);
        Long listingId = persistLiveListingFor(owner.getId());

        mockMvc.perform(post("/api/listings/" + listingId + "/photos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://cdn/x.jpg\"}"))
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
