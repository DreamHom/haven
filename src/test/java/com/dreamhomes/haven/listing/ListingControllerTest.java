package com.dreamhomes.haven.listing;

import com.dreamhomes.haven.auth.JwtPrincipal;
import com.dreamhomes.haven.auth.service.JwtService;
import com.dreamhomes.haven.common.config.SecurityConfig;
import com.dreamhomes.haven.property.dto.PropertySummary;
import com.dreamhomes.haven.property.model.PropertyType;
import com.dreamhomes.haven.user.model.Role;
import com.dreamhomes.haven.user.service.UserCredentialsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import com.dreamhomes.haven.listing.dto.CreateListingCommand;
import com.dreamhomes.haven.listing.dto.ListingWithProperty;
import com.dreamhomes.haven.listing.dto.UpdateListingCommand;
import com.dreamhomes.haven.listing.exception.InvalidListingTransitionException;
import com.dreamhomes.haven.listing.exception.ListingNotFoundException;
import com.dreamhomes.haven.listing.exception.NotPropertyOwnerException;
import com.dreamhomes.haven.listing.model.Listing;
import com.dreamhomes.haven.listing.model.ListingStatus;
import com.dreamhomes.haven.listing.model.ListingType;

/**
 * Controller-level contract for the listings endpoints. Verifies routing, response
 * shape, principal extraction, exception → status mapping, and that the OWNER-only
 * write endpoints carry @PreAuthorize. Per-validator behavior and per-status-transition
 * rules live in their own focused tests (validators, ListingServiceUpdateTest).
 */
@WebMvcTest(ListingController.class)
@Import({SecurityConfig.class, com.dreamhomes.haven.listing.ListingMapperImpl.class})
@TestPropertySource(properties = {
        "haven.rate-limit.enabled=false",
        "cors.allowed-origins=http://localhost:3000",
        "haven.jwt.expiration-ms=3600000",
        "haven.jwt.issuer=test-issuer",
        "haven.jwt.audience=test-audience"
})
class ListingControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    ListingService listingService;

    @MockBean
    JwtService jwtService;

    @MockBean
    UserCredentialsService userCredentialsService;

    @Test
    void ownerCreatingListingReturns201WithListingSummary() throws Exception {
        when(listingService.create(eq(99L), any(CreateListingCommand.class)))
                .thenAnswer(inv -> stubListing(123L, 99L, ListingStatus.LIVE));

        mockMvc.perform(post("/api/listings")
                        .with(asPrincipal(99L, Role.OWNER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "propertyId": 7,
                                  "listingType": "RENT",
                                  "askingPrice": 1500000.00
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", is(123)))
                .andExpect(jsonPath("$.status", is("LIVE")))
                .andExpect(jsonPath("$.currency", is("NGN")));
    }

    @Test
    void notPropertyOwnerExceptionMapsTo403() throws Exception {
        when(listingService.create(eq(99L), any(CreateListingCommand.class)))
                .thenThrow(new NotPropertyOwnerException());

        mockMvc.perform(post("/api/listings")
                        .with(asPrincipal(99L, Role.OWNER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "propertyId": 7,
                                  "listingType": "RENT",
                                  "askingPrice": 1000000.00
                                }
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void applicantRoleCannotCreateListingProvingPreAuthorizeIsWired() throws Exception {
        mockMvc.perform(post("/api/listings")
                        .with(asPrincipal(50L, Role.APPLICANT))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "propertyId": 7,
                                  "listingType": "RENT",
                                  "askingPrice": 1000000.00
                                }
                                """))
                .andExpect(status().isForbidden());

        verify(listingService, never()).create(any(), any());
    }

    @Test
    void publicBrowseReturnsLiveListingsWithEmbeddedPropertySummary() throws Exception {
        when(listingService.browsePublic(any())).thenReturn(
                new PageImpl<>(List.of(stubListingWithProperty(1L, 99L, ListingStatus.LIVE))));

        mockMvc.perform(get("/api/listings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].status", is("LIVE")))
                .andExpect(jsonPath("$.content[0].property.address", is("12 Lekki Phase 1, Lagos")))
                .andExpect(jsonPath("$.content[0].property.bedrooms", is(3)));
    }

    @Test
    void publicGetByIdReturnsListingWithEmbeddedPropertySummary() throws Exception {
        when(listingService.findPubliclyVisible(1L))
                .thenReturn(stubListingWithProperty(1L, 99L, ListingStatus.LIVE));

        mockMvc.perform(get("/api/listings/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(1)))
                .andExpect(jsonPath("$.property.id").exists())
                .andExpect(jsonPath("$.property.address", is("12 Lekki Phase 1, Lagos")));
    }

    @Test
    void publicGetByIdReturns404WhenServiceThrowsNotFound() throws Exception {
        when(listingService.findPubliclyVisible(404L))
                .thenThrow(new ListingNotFoundException(404L));

        mockMvc.perform(get("/api/listings/404"))
                .andExpect(status().isNotFound());
    }

    @Test
    void ownerPatchReturnsUpdatedListing() throws Exception {
        when(listingService.update(eq(99L), eq(50L), any(UpdateListingCommand.class)))
                .thenAnswer(inv -> stubListing(50L, 99L, ListingStatus.PAUSED));

        mockMvc.perform(patch("/api/listings/50")
                        .with(asPrincipal(99L, Role.OWNER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "status": "PAUSED" }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("PAUSED")));
    }

    @Test
    void invalidListingTransitionMapsTo400() throws Exception {
        when(listingService.update(eq(99L), eq(50L), any(UpdateListingCommand.class)))
                .thenThrow(new InvalidListingTransitionException(ListingStatus.CLOSED, ListingStatus.LIVE));

        mockMvc.perform(patch("/api/listings/50")
                        .with(asPrincipal(99L, Role.OWNER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "status": "LIVE" }
                                """))
                .andExpect(status().isBadRequest());
    }

    private static Listing stubListing(Long id, Long ownerId, ListingStatus status) {
        Instant now = Instant.now();
        return Listing.builder()
                .id(id).propertyId(7L).ownerId(ownerId)
                .listingType(ListingType.RENT)
                .askingPrice(new BigDecimal("1500000.00")).currency("NGN")
                .status(status)
                .createdAt(now).updatedAt(now)
                .build();
    }

    private static ListingWithProperty stubListingWithProperty(Long id, Long ownerId, ListingStatus status) {
        PropertySummary summary = new PropertySummary(7L, PropertyType.APARTMENT,
                "12 Lekki Phase 1, Lagos", 3, 2, null, null);
        return new ListingWithProperty(stubListing(id, ownerId, status), summary);
    }

    private static RequestPostProcessor asPrincipal(Long userId, Role role) {
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                new JwtPrincipal(userId, "x@example.com", role, 1),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role.name())));
        return authentication(auth);
    }
}
