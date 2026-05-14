package com.dreamhomes.haven.lead;

import com.dreamhomes.haven.lead.model.ListingLead;
import com.dreamhomes.haven.notification.NotificationRepository;
import com.dreamhomes.haven.notification.model.NotificationKind;
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

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end: applicant interest on a live listing, owner inbox with gated PII, reveal,
 * idempotent reveal, and guardrails (duplicate lead, non-live listing, non-owner list).
 */
@AutoConfigureMockMvc
class ListingLeadFlowEndToEndIT extends AbstractPostgresIT {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    JwtTestSupport jwtTestSupport;

    @Autowired
    ListingLeadRepository listingLeadRepository;

    @Autowired
    NotificationRepository notificationRepository;

    @Test
    void applicantSubmitsLeadOwnerListsRevealsAndGetsNotification() throws Exception {
        LiveListing ctx = createLiveListing();

        MvcResult submit = mockMvc.perform(post("/api/listings/" + ctx.listingId() + "/leads")
                        .header("Authorization", jwtTestSupport.bearerFor(ctx.applicant()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "message": "Very interested in viewing this weekend.",
                                  "contactPhone": "+2348012345678",
                                  "contactEmail": "applicant.lead@example.com"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.revealed").value(false))
                .andExpect(jsonPath("$.contactPhone").doesNotExist())
                .andExpect(jsonPath("$.contactEmail").doesNotExist())
                .andReturn();
        long leadId = readId(submit);

        assertThat(notificationRepository.findByRecipientIdOrderByCreatedAtDesc(ctx.owner().getId()))
                .anyMatch(n -> n.getKind() == NotificationKind.LISTING_LEAD_SUBMITTED);

        mockMvc.perform(get("/api/listings/" + ctx.listingId() + "/leads")
                        .header("Authorization", jwtTestSupport.bearerFor(ctx.owner())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value((int) leadId))
                .andExpect(jsonPath("$.content[0].revealed").value(false))
                .andExpect(jsonPath("$.content[0].contactPhone").doesNotExist());

        mockMvc.perform(get("/api/listings/" + ctx.listingId() + "/leads")
                        .header("Authorization", jwtTestSupport.bearerFor(ctx.applicant())))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/listings/" + ctx.listingId() + "/leads/" + leadId + "/reveal")
                        .header("Authorization", jwtTestSupport.bearerFor(ctx.owner())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.revealed").value(true))
                .andExpect(jsonPath("$.contactPhone").value("+2348012345678"))
                .andExpect(jsonPath("$.contactEmail").value("applicant.lead@example.com"));

        mockMvc.perform(post("/api/listings/" + ctx.listingId() + "/leads/" + leadId + "/reveal")
                        .header("Authorization", jwtTestSupport.bearerFor(ctx.owner())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contactPhone").value("+2348012345678"));

        ListingLead reloaded = listingLeadRepository.findById(leadId).orElseThrow();
        assertThat(reloaded.getRevealedAt()).isNotNull();

        mockMvc.perform(post("/api/listings/" + ctx.listingId() + "/leads")
                        .header("Authorization", jwtTestSupport.bearerFor(ctx.applicant()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "message": "Second try",
                                  "contactPhone": "+2348099999999",
                                  "contactEmail": "other@example.com"
                                }
                                """))
                .andExpect(status().isConflict());
    }

    @Test
    void adminCanListLeadsWithContactAlwaysPresent() throws Exception {
        LiveListing ctx = createLiveListing();
        User admin = jwtTestSupport.persistUser(Role.ADMIN);

        mockMvc.perform(post("/api/listings/" + ctx.listingId() + "/leads")
                        .header("Authorization", jwtTestSupport.bearerFor(ctx.applicant()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "message": "m",
                                  "contactPhone": "+2348111111111",
                                  "contactEmail": "lead-for-admin@example.com"
                                }
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/admin/listings/" + ctx.listingId() + "/leads")
                        .header("Authorization", jwtTestSupport.bearerFor(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].contactPhone").value("+2348111111111"))
                .andExpect(jsonPath("$.content[0].contactEmail").value("lead-for-admin@example.com"))
                .andExpect(jsonPath("$.content[0].revealedAt").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void pausedListingDoesNotAcceptLeads() throws Exception {
        LiveListing ctx = createLiveListing();
        mockMvc.perform(patch("/api/listings/" + ctx.listingId())
                        .header("Authorization", jwtTestSupport.bearerFor(ctx.owner()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"PAUSED\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/listings/" + ctx.listingId() + "/leads")
                        .header("Authorization", jwtTestSupport.bearerFor(ctx.applicant()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "message": "x",
                                  "contactPhone": "+2348000000000",
                                  "contactEmail": "x@example.com"
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void secondRevealDoesNotShiftRevealedAt() throws Exception {
        LiveListing ctx = createLiveListing();
        MvcResult submit = mockMvc.perform(post("/api/listings/" + ctx.listingId() + "/leads")
                        .header("Authorization", jwtTestSupport.bearerFor(ctx.applicant()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "message": "m",
                                  "contactPhone": "+2348000000001",
                                  "contactEmail": "stable@example.com"
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        long leadId = readId(submit);

        mockMvc.perform(post("/api/listings/" + ctx.listingId() + "/leads/" + leadId + "/reveal")
                        .header("Authorization", jwtTestSupport.bearerFor(ctx.owner())))
                .andExpect(status().isOk());

        java.time.Instant first = listingLeadRepository.findById(leadId).orElseThrow().getRevealedAt();
        assertThat(first).isNotNull();

        Thread.sleep(Duration.ofMillis(25));

        mockMvc.perform(post("/api/listings/" + ctx.listingId() + "/leads/" + leadId + "/reveal")
                        .header("Authorization", jwtTestSupport.bearerFor(ctx.owner())))
                .andExpect(status().isOk());

        java.time.Instant second = listingLeadRepository.findById(leadId).orElseThrow().getRevealedAt();
        assertThat(second).isEqualTo(first);
    }

    private LiveListing createLiveListing() throws Exception {
        User owner = jwtTestSupport.persistUser(Role.OWNER);
        User applicant = jwtTestSupport.persistUser(Role.APPLICANT);
        String ownerBearer = jwtTestSupport.bearerFor(owner);

        MvcResult propertyResult = mockMvc.perform(post("/api/properties")
                        .header("Authorization", ownerBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "type": "APARTMENT",
                                  "address": "Lead Test Lane 1",
                                  "bedrooms": 2,
                                  "bathrooms": 1,
                                  "sizeSqm": 90
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        long propertyId = readId(propertyResult);

        MvcResult listingResult = mockMvc.perform(post("/api/listings")
                        .header("Authorization", ownerBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "propertyId": %d,
                                  "listingType": "RENT",
                                  "askingPrice": 900000
                                }
                                """.formatted(propertyId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("LIVE"))
                .andReturn();
        long listingId = readId(listingResult);
        return new LiveListing(owner, applicant, listingId);
    }

    private long readId(MvcResult result) throws Exception {
        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        return json.get("id").asLong();
    }

    private record LiveListing(User owner, User applicant, long listingId) {
    }
}
