package com.dreamhomes.haven.admin;

import com.dreamhomes.haven.auth.JwtPrincipal;
import com.dreamhomes.haven.auth.service.JwtService;
import com.dreamhomes.haven.common.config.SecurityConfig;
import com.dreamhomes.haven.admin.dto.AdminListingLeadResponse;
import com.dreamhomes.haven.listing.dto.ListingResponse;
import com.dreamhomes.haven.listing.model.ListingStatus;
import com.dreamhomes.haven.listing.model.ListingType;
import com.dreamhomes.haven.user.model.Role;
import com.dreamhomes.haven.user.service.UserCredentialsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
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
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import com.dreamhomes.haven.admin.controller.AdminListingController;
import com.dreamhomes.haven.admin.service.AdminListingService;

@WebMvcTest(AdminListingController.class)
@Import({SecurityConfig.class, com.dreamhomes.haven.support.JwtCookieTestStubConfiguration.class})
@TestPropertySource(properties = {
        "haven.rate-limit.enabled=false",
        "cors.allowed-origins=http://localhost:3000",
        "haven.jwt.expiration-ms=3600000",
        "haven.jwt.issuer=test-issuer",
        "haven.jwt.audience=test-audience"
})
class AdminListingControllerTest {

    @Autowired MockMvc mockMvc;
    @MockBean AdminListingService adminListingService;
    @MockBean JwtService jwtService;
    @MockBean com.dreamhomes.haven.auth.blocklist.JwtBlocklistRepository jwtBlocklistRepository;
    @MockBean UserCredentialsService userCredentialsService;

    @Test
    void adminListsLeadsForListingReturnsPagedPayload() throws Exception {
        var page = new PageImpl<>(List.of(new AdminListingLeadResponse(
                9L, 11L, 3L, "hello", Instant.parse("2026-05-01T12:00:00Z"),
                null, "+234800", "a@b.com")), PageRequest.of(0, 20), 1);
        when(adminListingService.listingLeads(eq(11L), any())).thenReturn(page);

        mockMvc.perform(get("/api/admin/listings/11/leads").with(asPrincipal(7L, Role.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id", is(9)))
                .andExpect(jsonPath("$.content[0].contactEmail", is("a@b.com")));
    }

    @Test
    void adminApprovesListingReturns200WithApprovedAt() throws Exception {
        ListingResponse approved = listing(11L, ListingStatus.LIVE, Instant.now());
        when(adminListingService.approve(eq(7L), eq(11L), org.mockito.ArgumentMatchers.isNull())).thenReturn(approved);

        mockMvc.perform(post("/api/admin/listings/11/approve")
                        .with(asPrincipal(7L, Role.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(11)))
                .andExpect(jsonPath("$.approvedAt", is(notNullValue())));
    }

    @Test
    void ownerCannotApproveListingProvingPreAuthorizeIsWired() throws Exception {
        mockMvc.perform(post("/api/admin/listings/11/approve")
                        .with(asPrincipal(50L, Role.OWNER)))
                .andExpect(status().isForbidden());

        verify(adminListingService, never()).approve(any(), any(), any());
    }

    @Test
    void adminTakedownWithReasonReturns200WithClosedStatus() throws Exception {
        ListingResponse closed = listing(11L, ListingStatus.CLOSED, null);
        when(adminListingService.takedown(eq(7L), eq(11L), eq("policy violation"))).thenReturn(closed);

        mockMvc.perform(post("/api/admin/listings/11/takedown")
                        .with(asPrincipal(7L, Role.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "reason": "policy violation" }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("CLOSED")));
    }

    @Test
    void takedownWithoutReasonReturns400() throws Exception {
        mockMvc.perform(post("/api/admin/listings/11/takedown")
                        .with(asPrincipal(7L, Role.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "reason": "" }
                                """))
                .andExpect(status().isBadRequest());

        verify(adminListingService, never()).takedown(any(), any(), any());
    }

    private static ListingResponse listing(Long id, ListingStatus status, Instant approvedAt) {
        Instant now = Instant.now();
        return new ListingResponse(id, 1L, 50L, ListingType.SALE,
                new BigDecimal("80000000.00"), "NGN", null, null, null,
                null, null, null, null,
                null, false,
                status, approvedAt, 0L, now, now, null, null, null, null, null, null, null);
    }

    private static RequestPostProcessor asPrincipal(Long userId, Role role) {
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                new JwtPrincipal(userId, "x@example.com", role, 1),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role.name())));
        return authentication(auth);
    }
}
