package com.dreamhomes.haven.offer;

import com.dreamhomes.haven.auth.JwtPrincipal;
import com.dreamhomes.haven.auth.JwtService;
import com.dreamhomes.haven.common.config.SecurityConfig;
import com.dreamhomes.haven.user.Role;
import com.dreamhomes.haven.user.UserCredentialsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OfferController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = {
        "haven.rate-limit.enabled=false",
        "cors.allowed-origins=http://localhost:3000",
        "jwt.secret=test-secret-not-a-placeholder-and-32-bytes-or-more",
        "jwt.expiration-ms=3600000",
        "jwt.issuer=test-issuer",
        "jwt.audience=test-audience"
})
class OfferControllerTest {

    @Autowired MockMvc mockMvc;
    @MockBean OfferService offerService;
    @MockBean JwtService jwtService;
    @MockBean UserCredentialsService userCredentialsService;

    @Test
    void applicantSubmitsOfferReturns201WithSummary() throws Exception {
        when(offerService.submit(eq(100L), any(SubmitOfferCommand.class)))
                .thenAnswer(inv -> stub(123L, OfferStatus.PENDING));

        mockMvc.perform(post("/api/offers")
                        .with(asPrincipal(100L, Role.APPLICANT))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "listingId": 7,
                                  "amount": 75000000.00,
                                  "currency": "NGN",
                                  "message": "Cash buyer"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", is(123)))
                .andExpect(jsonPath("$.status", is("PENDING")));
    }

    @Test
    void ownerCannotSubmitOfferProvingPreAuthorizeIsWired() throws Exception {
        mockMvc.perform(post("/api/offers")
                        .with(asPrincipal(99L, Role.OWNER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "listingId": 7, "amount": 100 }
                                """))
                .andExpect(status().isForbidden());

        verify(offerService, never()).submit(any(), any());
    }

    @Test
    void ownerAcceptsOfferReturns200WithUpdatedStatus() throws Exception {
        when(offerService.respond(eq(99L), eq(50L), eq(OfferStatus.ACCEPTED)))
                .thenAnswer(inv -> stub(50L, OfferStatus.ACCEPTED));

        mockMvc.perform(patch("/api/offers/50")
                        .with(asPrincipal(99L, Role.OWNER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "status": "ACCEPTED" }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("ACCEPTED")));
    }

    @Test
    void adminCannotPatchOfferProvingPreAuthorizeIsWired() throws Exception {
        // Phase 13: PATCH is now open to OWNER and APPLICANT (applicants accept owner
        // counters). ADMIN is deliberately NOT in the allowlist — admin moderation goes
        // through dedicated /api/admin/* endpoints; offers are between the two parties.
        mockMvc.perform(patch("/api/offers/50")
                        .with(asPrincipal(1L, Role.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "status": "ACCEPTED" }
                                """))
                .andExpect(status().isForbidden());

        verify(offerService, never()).respond(any(), any(), any());
    }

    private static Offer stub(Long id, OfferStatus status) {
        Instant now = Instant.now();
        return Offer.builder()
                .id(id).listingId(7L).applicantId(100L).ownerId(99L)
                .amount(new BigDecimal("75000000.00")).currency("NGN")
                .status(status)
                .createdAt(now).updatedAt(now).build();
    }

    private static RequestPostProcessor asPrincipal(Long userId, Role role) {
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                new JwtPrincipal(userId, "x@example.com", role, 1),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role.name())));
        return authentication(auth);
    }
}
