package com.dreamhomes.haven.inspection;

import com.dreamhomes.haven.auth.JwtPrincipal;
import com.dreamhomes.haven.auth.service.JwtService;
import com.dreamhomes.haven.common.config.SecurityConfig;
import com.dreamhomes.haven.user.model.Role;
import com.dreamhomes.haven.user.service.UserCredentialsService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import com.dreamhomes.haven.inspection.dto.CreateSlotCommand;
import com.dreamhomes.haven.inspection.model.InspectionSlot;
import com.dreamhomes.haven.inspection.controller.InspectionSlotController;
import com.dreamhomes.haven.inspection.service.InspectionSlotService;

@WebMvcTest(InspectionSlotController.class)
@Import({SecurityConfig.class, com.dreamhomes.haven.inspection.mapping.InspectionSlotMapperImpl.class})
@TestPropertySource(properties = {
        "haven.rate-limit.enabled=false",
        "cors.allowed-origins=http://localhost:3000",
        "haven.jwt.expiration-ms=3600000",
        "haven.jwt.issuer=test-issuer",
        "haven.jwt.audience=test-audience"
})
class InspectionSlotControllerTest {

    @Autowired MockMvc mockMvc;
    @MockBean InspectionSlotService slotService;
    @MockBean JwtService jwtService;
    @MockBean UserCredentialsService userCredentialsService;

    @Test
    void ownerCreatesSlotReturns201WithSlotSummary() throws Exception {
        when(slotService.create(eq(99L), eq(7L), any(CreateSlotCommand.class)))
                .thenAnswer(inv -> InspectionSlot.builder()
                        .id(123L).listingId(7L)
                        .startsAt(Instant.parse("2026-06-01T10:00:00Z"))
                        .endsAt(Instant.parse("2026-06-01T11:00:00Z"))
                        .createdAt(Instant.now()).build());

        mockMvc.perform(post("/api/listings/7/slots")
                        .with(asPrincipal(99L, Role.OWNER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "startsAt": "2026-06-01T10:00:00Z",
                                  "endsAt":   "2026-06-01T11:00:00Z"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", is(123)))
                .andExpect(jsonPath("$.listingId", is(7)));
    }

    @Test
    void applicantCannotCreateSlotProvingPreAuthorizeIsWired() throws Exception {
        mockMvc.perform(post("/api/listings/7/slots")
                        .with(asPrincipal(50L, Role.APPLICANT))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "startsAt": "2026-06-01T10:00:00Z",
                                  "endsAt":   "2026-06-01T11:00:00Z"
                                }
                                """))
                .andExpect(status().isForbidden());

        verify(slotService, never()).create(any(), any(), any());
    }

    @Test
    void publicGetReturnsAvailableSlotsForListing() throws Exception {
        when(slotService.listAvailableForListing(7L)).thenReturn(List.of(
                InspectionSlot.builder().id(1L).listingId(7L)
                        .startsAt(Instant.parse("2026-06-01T10:00:00Z"))
                        .endsAt(Instant.parse("2026-06-01T11:00:00Z"))
                        .createdAt(Instant.now()).build()));

        mockMvc.perform(get("/api/listings/7/slots"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id", is(1)))
                .andExpect(jsonPath("$[0].listingId", is(7)));
    }

    private static RequestPostProcessor asPrincipal(Long userId, Role role) {
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                new JwtPrincipal(userId, "x@example.com", role, 1),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role.name())));
        return authentication(auth);
    }
}
