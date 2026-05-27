package com.dreamhomes.haven.listing;

import com.dreamhomes.haven.support.AbstractPostgresIT;
import com.dreamhomes.haven.support.JwtTestSupport;
import com.dreamhomes.haven.user.model.Role;
import com.dreamhomes.haven.user.model.User;
import com.dreamhomes.haven.user.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;

import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration coverage for Item 16 — listing payloads embed the owner-side trust signal
 * ({@code ownerIdentityVerifiedAt}) so Vista can render the "⚠️ Possible Scam" warning
 * chip and the "✓ Verified" badge without N+1 fetches from the browse / detail surface.
 */
@AutoConfigureMockMvc
class ListingTrustSignalsIT extends AbstractPostgresIT {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserRepository userRepository;
    @Autowired JwtTestSupport jwtTestSupport;

    @Test
    void listingDetailReturnsNullOwnerIdentityVerifiedAtWhenOwnerHasNotCompletedVerification()
            throws Exception {
        User owner = jwtTestSupport.persistUser(Role.OWNER);
        Long listingId = createPropertyAndListing(jwtTestSupport.bearerFor(owner));

        mockMvc.perform(get("/api/listings/" + listingId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(listingId.intValue()))
                .andExpect(jsonPath("$.ownerIdentityVerifiedAt").value(nullValue()));
    }

    @Test
    void listingDetailReturnsOwnerIdentityVerifiedAtTimestampWhenOwnerIsVerified()
            throws Exception {
        User owner = jwtTestSupport.persistUser(Role.OWNER);
        Long listingId = createPropertyAndListing(jwtTestSupport.bearerFor(owner));

        // Simulate admin approval by stamping the verification timestamp on the owner row.
        owner.setIdentityVerifiedAt(Instant.parse("2026-04-12T10:00:00Z"));
        userRepository.save(owner);

        mockMvc.perform(get("/api/listings/" + listingId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(listingId.intValue()))
                .andExpect(jsonPath("$.ownerIdentityVerifiedAt").value(notNullValue()));
    }

    @Test
    void browseFeedAlsoCarriesOwnerIdentityVerifiedAtSoCardsRenderWithoutNPlusOne()
            throws Exception {
        User owner = jwtTestSupport.persistUser(Role.OWNER);
        Long listingId = createPropertyAndListing(jwtTestSupport.bearerFor(owner));

        owner.setIdentityVerifiedAt(Instant.parse("2026-04-12T10:00:00Z"));
        userRepository.save(owner);

        mockMvc.perform(get("/api/listings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.id == " + listingId + ")].ownerIdentityVerifiedAt")
                        .value(org.hamcrest.Matchers.hasItem(notNullValue())));
    }

    private Long createPropertyAndListing(String bearer) throws Exception {
        Long propertyId = readId(mockMvc.perform(post("/api/properties")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "type": "APARTMENT",
                                  "address": "8 Trust Signal Way, Lekki",
                                  "bedrooms": 2,
                                  "bathrooms": 2
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn());
        return readId(mockMvc.perform(post("/api/listings")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "propertyId": %d,
                                  "listingType": "RENT",
                                  "askingPrice": 1200000.00
                                }
                                """.formatted(propertyId)))
                .andExpect(status().isCreated())
                .andReturn());
    }

    private Long readId(MvcResult result) throws Exception {
        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        return json.get("id").asLong();
    }
}
