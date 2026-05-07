package com.dreamhomes.haven.listing;

import com.dreamhomes.haven.auth.JwtTestSupport;
import com.dreamhomes.haven.common.AbstractPostgresIT;
import com.dreamhomes.haven.listing.ListingRepository;
import com.dreamhomes.haven.property.PropertyRepository;
import com.dreamhomes.haven.user.AgentProfileRepository;
import com.dreamhomes.haven.user.Role;
import com.dreamhomes.haven.user.User;
import com.dreamhomes.haven.user.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The full owner workflow against a real Postgres + the real security chain:
 * register → create property → create listing → public sees it → owner pauses →
 * public no longer sees it → owner closes → owner cannot reopen.
 *
 * <p>If any link in the chain breaks, this fails before any single feature test does.
 */
@AutoConfigureMockMvc
class ListingFlowEndToEndIT extends AbstractPostgresIT {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    UserRepository userRepository;

    @Autowired
    AgentProfileRepository agentProfileRepository;

    @Autowired
    PropertyRepository propertyRepository;

    @Autowired
    ListingRepository listingRepository;

    @Autowired
    JwtTestSupport jwtTestSupport;

    @Autowired
    com.dreamhomes.haven.notification.NotificationRepository notificationRepository;

    @Autowired
    com.dreamhomes.haven.inspection.InspectionRequestRepository inspectionRequestRepository;

    @Autowired
    com.dreamhomes.haven.inspection.InspectionSlotRepository inspectionSlotRepository;

    @BeforeEach
    @org.junit.jupiter.api.AfterEach
    void clean() {
        // Run before AND after each test so we never leak state to a sibling IT
        // that uses @DataJpaTest-style transactional rollback (those tests still
        // see committed rows from other tests' transactions).
        notificationRepository.deleteAll();
        inspectionRequestRepository.deleteAll();
        inspectionSlotRepository.deleteAll();
        listingRepository.deleteAll();
        propertyRepository.deleteAll();
        agentProfileRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void ownerLifecycleFromCreateToCloseWithPublicVisibilityFollowing() throws Exception {
        User owner = jwtTestSupport.persistUser(Role.OWNER);
        String bearer = jwtTestSupport.bearerFor(owner);

        // 1. Owner creates a property.
        MvcResult propertyResult = mockMvc.perform(post("/api/properties")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "type": "APARTMENT",
                                  "address": "12 Lekki Phase 1, Lagos",
                                  "bedrooms": 3,
                                  "bathrooms": 2,
                                  "sizeSqm": 128.50
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        Long propertyId = readId(propertyResult);

        // 2. Owner creates a LIVE listing for that property.
        MvcResult listingResult = mockMvc.perform(post("/api/listings")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "propertyId": %d,
                                  "listingType": "RENT",
                                  "askingPrice": 1500000.00
                                }
                                """.formatted(propertyId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("LIVE"))
                .andReturn();
        Long listingId = readId(listingResult);

        // 3. Public can browse it (no auth header), and the response embeds the property
        // summary so the frontend can render a card without an extra round trip.
        mockMvc.perform(get("/api/listings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(listingId.intValue()))
                .andExpect(jsonPath("$.content[0].property.address").value("12 Lekki Phase 1, Lagos"))
                .andExpect(jsonPath("$.content[0].property.bedrooms").value(3));

        // 4. Owner pauses it.
        mockMvc.perform(patch("/api/listings/" + listingId)
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"PAUSED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAUSED"));

        // 5. Public no longer sees it: list is empty AND direct GET 404s.
        mockMvc.perform(get("/api/listings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty());
        mockMvc.perform(get("/api/listings/" + listingId))
                .andExpect(status().isNotFound());

        // 6. Owner closes the listing.
        mockMvc.perform(patch("/api/listings/" + listingId)
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"CLOSED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"));

        // 7. CLOSED is terminal — owner cannot reopen to LIVE.
        mockMvc.perform(patch("/api/listings/" + listingId)
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"LIVE\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void applicantCannotCreatePropertyOrListing() throws Exception {
        User applicant = jwtTestSupport.persistUser(Role.APPLICANT);
        String bearer = jwtTestSupport.bearerFor(applicant);

        mockMvc.perform(post("/api/properties")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "type": "APARTMENT",
                                  "address": "any",
                                  "bedrooms": 1,
                                  "bathrooms": 1
                                }
                                """))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/listings")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "propertyId": 1,
                                  "listingType": "SALE",
                                  "askingPrice": 100.00
                                }
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void ownerCannotPatchAnotherOwnersListing() throws Exception {
        User ownerA = jwtTestSupport.persistUser(Role.OWNER);
        User ownerB = jwtTestSupport.persistUser(Role.OWNER);

        // ownerA creates a property + listing.
        Long propertyId = readId(mockMvc.perform(post("/api/properties")
                        .header("Authorization", jwtTestSupport.bearerFor(ownerA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "type": "HOUSE", "address": "ownerA", "bedrooms": 4, "bathrooms": 3 }
                                """))
                .andReturn());
        Long listingId = readId(mockMvc.perform(post("/api/listings")
                        .header("Authorization", jwtTestSupport.bearerFor(ownerA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "propertyId": %d, "listingType": "SALE", "askingPrice": 75000000 }
                                """.formatted(propertyId)))
                .andReturn());

        // ownerB tries to patch.
        mockMvc.perform(patch("/api/listings/" + listingId)
                        .header("Authorization", jwtTestSupport.bearerFor(ownerB))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"PAUSED\"}"))
                .andExpect(status().isForbidden());
    }

    private Long readId(MvcResult result) throws Exception {
        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        Long id = json.get("id").asLong();
        assertThat(id).isNotNull().isPositive();
        return id;
    }
}
