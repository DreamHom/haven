package com.dreamhomes.haven.listingreport;

import com.dreamhomes.haven.listing.ListingRepository;
import com.dreamhomes.haven.listing.model.Listing;
import com.dreamhomes.haven.listing.model.ListingStatus;
import com.dreamhomes.haven.listing.model.ListingType;
import com.dreamhomes.haven.notification.NotificationRepository;
import com.dreamhomes.haven.property.PropertyRepository;
import com.dreamhomes.haven.property.model.Property;
import com.dreamhomes.haven.property.model.PropertyType;
import com.dreamhomes.haven.support.AbstractPostgresIT;
import com.dreamhomes.haven.support.JwtTestSupport;
import com.dreamhomes.haven.user.model.Role;
import com.dreamhomes.haven.user.model.User;
import com.dreamhomes.haven.user.repository.UserRepository;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class ListingReportIT extends AbstractPostgresIT {

    @Autowired MockMvc mockMvc;
    @Autowired JwtTestSupport jwtTestSupport;
    @Autowired UserRepository userRepository;
    @Autowired PropertyRepository propertyRepository;
    @Autowired ListingRepository listingRepository;
    @Autowired ListingReportRepository listingReportRepository;
    @Autowired NotificationRepository notificationRepository;


    @Test
    void authenticatedUserReportsLiveListingAndAdminGetsNotified() throws Exception {
        User admin = jwtTestSupport.persistUser(Role.ADMIN);
        User owner = jwtTestSupport.persistUser(Role.OWNER);
        User reporter = jwtTestSupport.persistUser(Role.APPLICANT);
        Long listingId = persistLiveListingFor(owner.getId());

        mockMvc.perform(post("/api/listings/" + listingId + "/report")
                        .header("Authorization", jwtTestSupport.bearerFor(reporter))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reason": "OFF_PLATFORM_FEES",
                                  "details": "Asking for ₦200k 'inspection fee' off-platform"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.listingId").value(listingId))
                .andExpect(jsonPath("$.reason").value("OFF_PLATFORM_FEES"))
                .andExpect(jsonPath("$.details").value("Asking for ₦200k 'inspection fee' off-platform"));

        // Row landed.
        assertThat(listingReportRepository.existsByListingIdAndReporterUserId(listingId, reporter.getId())).isTrue();

        // The admin we seeded got a LISTING_REPORTED notification.
        long adminNotifications = notificationRepository.findAll().stream()
                .filter(n -> n.getRecipientId().equals(admin.getId()))
                .filter(n -> "LISTING_REPORTED".equals(n.getKind().name()))
                .count();
        assertThat(adminNotifications).isEqualTo(1);
    }

    @Test
    void duplicateReportFromSameUserReturns409() throws Exception {
        User owner = jwtTestSupport.persistUser(Role.OWNER);
        User reporter = jwtTestSupport.persistUser(Role.APPLICANT);
        Long listingId = persistLiveListingFor(owner.getId());
        String bearer = jwtTestSupport.bearerFor(reporter);

        // First report → 201.
        mockMvc.perform(post("/api/listings/" + listingId + "/report")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "reason": "SCAM" }
                                """))
                .andExpect(status().isCreated());

        // Same reporter, same listing → 409.
        mockMvc.perform(post("/api/listings/" + listingId + "/report")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "reason": "SCAM" }
                                """))
                .andExpect(status().isConflict());

        assertThat(listingReportRepository.findAll()).hasSize(1);
    }

    @Test
    void anonymousCallerCannotReport() throws Exception {
        User owner = jwtTestSupport.persistUser(Role.OWNER);
        Long listingId = persistLiveListingFor(owner.getId());

        mockMvc.perform(post("/api/listings/" + listingId + "/report")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "reason": "SCAM" }
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void reportingMissingListingReturns404() throws Exception {
        User reporter = jwtTestSupport.persistUser(Role.APPLICANT);

        mockMvc.perform(post("/api/listings/999999/report")
                        .header("Authorization", jwtTestSupport.bearerFor(reporter))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "reason": "SCAM" }
                                """))
                .andExpect(status().isNotFound());
    }

    @Test
    void invalidReasonReturns400() throws Exception {
        User owner = jwtTestSupport.persistUser(Role.OWNER);
        User reporter = jwtTestSupport.persistUser(Role.APPLICANT);
        Long listingId = persistLiveListingFor(owner.getId());

        mockMvc.perform(post("/api/listings/" + listingId + "/report")
                        .header("Authorization", jwtTestSupport.bearerFor(reporter))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "reason": "MADE_UP_VALUE" }
                                """))
                .andExpect(status().isBadRequest());
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
