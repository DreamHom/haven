package com.dreamhomes.haven.offer;

import com.dreamhomes.haven.auth.JwtTestSupport;
import com.dreamhomes.haven.common.AbstractPostgresIT;
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
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The full offer flow end-to-end across HTTP, Postgres, Kafka, the listener, and the
 * notification module:
 *
 * <ol>
 *   <li>Owner has a LIVE listing.</li>
 *   <li>Applicant submits an offer via POST /api/offers.</li>
 *   <li>{@code OFFER_SUBMITTED} fires on Kafka.</li>
 *   <li>Listener creates a Notification for the owner.</li>
 *   <li>Owner accepts via PATCH /api/offers/{id}.</li>
 *   <li>Trying to PATCH the now-ACCEPTED offer returns 400 (terminal status).</li>
 * </ol>
 */
@AutoConfigureMockMvc
class OfferFlowEndToEndIT extends AbstractPostgresIT {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JwtTestSupport jwtTestSupport;

    @Autowired UserRepository userRepository;
    @Autowired AgentProfileRepository agentProfileRepository;
    @Autowired PropertyRepository propertyRepository;
    @Autowired ListingRepository listingRepository;
    @Autowired OfferRepository offerRepository;
    @Autowired NotificationRepository notificationRepository;

    @BeforeEach
    @AfterEach
    void clean() {
        notificationRepository.deleteAll();
        offerRepository.deleteAll();
        listingRepository.deleteAll();
        propertyRepository.deleteAll();
        agentProfileRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void applicantSubmitsOfferOwnerGetsNotifiedThenAcceptsAndCannotChangeAgain() throws Exception {
        User owner = jwtTestSupport.persistUser(Role.OWNER);
        User applicant = jwtTestSupport.persistUser(Role.APPLICANT);
        Long listingId = persistLiveListingFor(owner.getId());

        // 1. Applicant submits offer.
        MvcResult submitResult = mockMvc.perform(post("/api/offers")
                        .header("Authorization", jwtTestSupport.bearerFor(applicant))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "listingId": %d,
                                  "amount": 75000000.00,
                                  "currency": "NGN",
                                  "message": "Cash buyer"
                                }
                                """.formatted(listingId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn();
        Long offerId = readId(submitResult);

        // 2. Listener processes the Kafka event asynchronously — wait for the row.
        Awaitility.await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            List<Notification> ownerNotifs = notificationRepository
                    .findByRecipientIdOrderByCreatedAtDesc(owner.getId());
            assertThat(ownerNotifs).hasSize(1);
            assertThat(ownerNotifs.get(0).getKind()).isEqualTo(NotificationKind.OFFER_SUBMITTED);
            assertThat(ownerNotifs.get(0).getPayload())
                    .contains("\"offerId\":" + offerId)
                    .contains("\"applicantId\":" + applicant.getId());
        });

        // 3. Owner accepts the offer.
        mockMvc.perform(patch("/api/offers/" + offerId)
                        .header("Authorization", jwtTestSupport.bearerFor(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"status\": \"ACCEPTED\" }"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED"));

        // 4. Acceptance is terminal — owner can't decline-after-accept.
        mockMvc.perform(patch("/api/offers/" + offerId)
                        .header("Authorization", jwtTestSupport.bearerFor(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"status\": \"DECLINED\" }"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void applicantOfferOnPausedListingIsRejectedWith400() throws Exception {
        User owner = jwtTestSupport.persistUser(Role.OWNER);
        User applicant = jwtTestSupport.persistUser(Role.APPLICANT);
        Long listingId = persistListingFor(owner.getId(), ListingStatus.PAUSED);

        mockMvc.perform(post("/api/offers")
                        .header("Authorization", jwtTestSupport.bearerFor(applicant))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "listingId": %d, "amount": 100 }
                                """.formatted(listingId)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void anotherOwnerCannotRespondToAnOfferTheyDoNotOwn() throws Exception {
        User ownerA = jwtTestSupport.persistUser(Role.OWNER);
        User ownerB = jwtTestSupport.persistUser(Role.OWNER);
        User applicant = jwtTestSupport.persistUser(Role.APPLICANT);
        Long listingId = persistLiveListingFor(ownerA.getId());

        Long offerId = readId(mockMvc.perform(post("/api/offers")
                        .header("Authorization", jwtTestSupport.bearerFor(applicant))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "listingId": %d, "amount": 100 }
                                """.formatted(listingId)))
                .andReturn());

        mockMvc.perform(patch("/api/offers/" + offerId)
                        .header("Authorization", jwtTestSupport.bearerFor(ownerB))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"status\": \"ACCEPTED\" }"))
                .andExpect(status().isForbidden());
    }

    private Long persistLiveListingFor(Long ownerId) {
        return persistListingFor(ownerId, ListingStatus.LIVE);
    }

    private Long persistListingFor(Long ownerId, ListingStatus status) {
        Property property = propertyRepository.save(Property.builder()
                .ownerId(ownerId).type(PropertyType.HOUSE)
                .address("Address").bedrooms(3).bathrooms(2)
                .createdAt(Instant.now()).build());
        Listing listing = listingRepository.save(Listing.builder()
                .propertyId(property.getId()).ownerId(ownerId)
                .listingType(ListingType.SALE).askingPrice(new BigDecimal("80000000.00")).currency("NGN")
                .status(status)
                .createdAt(Instant.now()).updatedAt(Instant.now()).build());
        return listing.getId();
    }

    private Long readId(MvcResult result) throws Exception {
        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        return json.get("id").asLong();
    }
}
