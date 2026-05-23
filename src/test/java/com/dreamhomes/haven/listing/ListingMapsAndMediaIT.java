package com.dreamhomes.haven.listing;

import com.dreamhomes.haven.support.AbstractPostgresIT;
import com.dreamhomes.haven.support.JwtTestSupport;
import com.dreamhomes.haven.user.model.Role;
import com.dreamhomes.haven.user.model.User;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 * Exercises the public listing surface for <strong>maps</strong> (property lat/lng on the
 * embedded {@code property} summary) and <strong>extra media</strong> beyond a single
 * virtual tour: {@code floorPlanUrl} on the listing row plus ordered {@code /videos}.
 *
 * <p>This is the automated equivalent of calling {@code GET /api/listings},
 * {@code PATCH /api/properties/{id}}, and {@code GET/POST .../videos} against a real DB
 * after Flyway (including V37 Lagos backfill for <em>pre-existing</em> null pins — new rows
 * created without coordinates stay null until patched).
 */
@AutoConfigureMockMvc
class ListingMapsAndMediaIT extends AbstractPostgresIT {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    JwtTestSupport jwtTestSupport;

    @Test
    void browseEmbedsPropertyCoordsAndListingMediaUrls() throws Exception {
        User owner = jwtTestSupport.persistUser(Role.OWNER);
        String bearer = jwtTestSupport.bearerFor(owner);

        MvcResult propertyResult = mockMvc.perform(post("/api/properties")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "type": "APARTMENT",
                                  "address": "44 Admiralty Way, Lekki Phase 1",
                                  "bedrooms": 2,
                                  "bathrooms": 2,
                                  "sizeSqm": 95.0
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        long propertyId = readId(propertyResult);

        mockMvc.perform(get("/api/listings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty());

        MvcResult listingResult = mockMvc.perform(post("/api/listings")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "propertyId": %d,
                                  "listingType": "RENT",
                                  "askingPrice": 2200000.00,
                                  "title": "Sea breeze corner",
                                  "virtualTourUrl": "https://my.matterport.com/show/?m=demo",
                                  "floorPlanUrl": "https://cdn.example.com/fp/lekki-44.pdf",
                                  "priceNegotiable": true
                                }
                                """.formatted(propertyId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.virtualTourUrl").value("https://my.matterport.com/show/?m=demo"))
                .andExpect(jsonPath("$.floorPlanUrl").value("https://cdn.example.com/fp/lekki-44.pdf"))
                .andReturn();
        long listingId = readId(listingResult);

        mockMvc.perform(get("/api/listings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value((int) listingId))
                .andExpect(jsonPath("$.content[0].virtualTourUrl")
                        .value("https://my.matterport.com/show/?m=demo"))
                .andExpect(jsonPath("$.content[0].floorPlanUrl")
                        .value("https://cdn.example.com/fp/lekki-44.pdf"))
                .andExpect(jsonPath("$.content[0].property.latitude").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.content[0].property.longitude").value(org.hamcrest.Matchers.nullValue()));

        mockMvc.perform(patch("/api/properties/" + propertyId)
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "latitude": 6.4381, "longitude": 3.4739 }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.latitude").value(6.4381))
                .andExpect(jsonPath("$.longitude").value(3.4739));

        mockMvc.perform(get("/api/listings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].property.latitude").value(6.4381))
                .andExpect(jsonPath("$.content[0].property.longitude").value(3.4739));

        mockMvc.perform(post("/api/listings/" + listingId + "/videos")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "url": "https://www.youtube.com/watch?v=dQw4w9WgXcQ", "caption": "Walkthrough" }
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/listings/" + listingId + "/videos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].url").value("https://www.youtube.com/watch?v=dQw4w9WgXcQ"))
                .andExpect(jsonPath("$[0].caption").value("Walkthrough"));
    }

    private long readId(MvcResult result) throws Exception {
        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        long id = json.get("id").asLong();
        assertThat(id).isPositive();
        return id;
    }
}
