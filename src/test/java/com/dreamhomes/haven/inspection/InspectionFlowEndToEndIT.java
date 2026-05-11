package com.dreamhomes.haven.inspection;

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
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.awaitility.Awaitility;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import com.dreamhomes.haven.notification.InspectionRequestedListener;
import com.dreamhomes.haven.notification.NotificationService;
import com.dreamhomes.haven.offer.OfferRepository;
import com.dreamhomes.haven.inspection.repository.InspectionRequestRepository;
import com.dreamhomes.haven.inspection.repository.InspectionSlotRepository;

/**
 * The full inspection flow end-to-end across HTTP, Postgres, Kafka (embedded broker),
 * the listener, and the notification module:
 *
 * <ol>
 *   <li>Owner registers and creates a listing.</li>
 *   <li>Owner publishes an inspection slot.</li>
 *   <li>Public anonymous GET sees the slot.</li>
 *   <li>Applicant registers and POSTs an inspection request.</li>
 *   <li>{@code INSPECTION_REQUESTED} fires through Kafka.</li>
 *   <li>{@code InspectionRequestedListener} consumes it and {@code NotificationService}
 *       persists a notification for the owner.</li>
 *   <li>A second applicant trying the same slot is rejected with 409 (DB partial unique index).</li>
 * </ol>
 */
@AutoConfigureMockMvc
class InspectionFlowEndToEndIT extends AbstractPostgresIT {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JwtTestSupport jwtTestSupport;

    @Autowired UserRepository userRepository;
    @Autowired AgentProfileRepository agentProfileRepository;
    @Autowired PropertyRepository propertyRepository;
    @Autowired ListingRepository listingRepository;
    @Autowired InspectionSlotRepository slotRepository;
    @Autowired InspectionRequestRepository requestRepository;
    @Autowired NotificationRepository notificationRepository;
    @Autowired com.dreamhomes.haven.offer.OfferRepository offerRepository;


    @Test
    void ownerPublishesSlotApplicantRequestsItNotificationLands() throws Exception {
        User owner = jwtTestSupport.persistUser(Role.OWNER);
        User applicant = jwtTestSupport.persistUser(Role.APPLICANT);

        // Owner has a listing (set up directly — listing creation is covered elsewhere).
        Long listingId = persistLiveListingFor(owner.getId());

        // 1. Owner publishes a slot via the API.
        MvcResult slotResult = mockMvc.perform(post("/api/listings/" + listingId + "/slots")
                        .header("Authorization", jwtTestSupport.bearerFor(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "startsAt": "2026-06-01T10:00:00Z",
                                  "endsAt":   "2026-06-01T11:00:00Z"
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        Long slotId = readId(slotResult);

        // 2. Public sees the slot via the listing-scoped GET.
        mockMvc.perform(get("/api/listings/" + listingId + "/slots"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(slotId.intValue()));

        // 3. Applicant claims the slot.
        mockMvc.perform(post("/api/inspections")
                        .header("Authorization", jwtTestSupport.bearerFor(applicant))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "slotId": %d, "notes": "I can come Saturday morning" }
                                """.formatted(slotId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"));

        // 4. Listener processes the Kafka event asynchronously — wait for the
        // notification row to land for the owner.
        Awaitility.await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            List<Notification> ownerNotifs = notificationRepository
                    .findByRecipientIdOrderByCreatedAtDesc(owner.getId());
            assertThat(ownerNotifs).hasSize(1);
            assertThat(ownerNotifs.get(0).getKind()).isEqualTo(NotificationKind.INSPECTION_REQUESTED);
            assertThat(ownerNotifs.get(0).getPayload())
                    .contains("\"slotId\":" + slotId)
                    .contains("\"applicantId\":" + applicant.getId());
        });

        // 5. A second applicant trying the same slot is rejected — partial unique index.
        User applicantTwo = jwtTestSupport.persistUser(Role.APPLICANT);
        mockMvc.perform(post("/api/inspections")
                        .header("Authorization", jwtTestSupport.bearerFor(applicantTwo))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "slotId": %d }
                                """.formatted(slotId)))
                .andExpect(status().isConflict());
    }

    private Long persistLiveListingFor(Long ownerId) {
        Property property = propertyRepository.save(Property.builder()
                .ownerId(ownerId).type(PropertyType.HOUSE)
                .address("12 Lekki Phase 1, Lagos").bedrooms(3).bathrooms(2)
                .createdAt(Instant.now()).build());
        Listing listing = listingRepository.save(Listing.builder()
                .propertyId(property.getId()).ownerId(ownerId)
                .listingType(ListingType.RENT).askingPrice(new BigDecimal("1500000.00")).currency("NGN")
                .status(ListingStatus.LIVE)
                .createdAt(Instant.now()).updatedAt(Instant.now()).build());
        return listing.getId();
    }

    private Long readId(MvcResult result) throws Exception {
        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        return json.get("id").asLong();
    }
}
