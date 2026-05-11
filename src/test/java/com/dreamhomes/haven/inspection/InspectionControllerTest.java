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
import com.dreamhomes.haven.inspection.dto.RequestInspectionCommand;
import com.dreamhomes.haven.inspection.model.InspectionRequest;
import com.dreamhomes.haven.inspection.model.InspectionRequestStatus;
import com.dreamhomes.haven.inspection.controller.InspectionController;
import com.dreamhomes.haven.inspection.service.InspectionService;

@WebMvcTest(InspectionController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = {
        "haven.rate-limit.enabled=false",
        "cors.allowed-origins=http://localhost:3000",
        "haven.jwt.expiration-ms=3600000",
        "haven.jwt.issuer=test-issuer",
        "haven.jwt.audience=test-audience"
})
class InspectionControllerTest {

    @Autowired MockMvc mockMvc;
    @MockBean InspectionService inspectionService;
    @MockBean JwtService jwtService;
    @MockBean UserCredentialsService userCredentialsService;

    @Test
    void applicantSubmitsRequestReturns201WithRequestSummary() throws Exception {
        when(inspectionService.requestSlot(eq(100L), any(RequestInspectionCommand.class)))
                .thenAnswer(inv -> InspectionRequest.builder()
                        .id(123L).slotId(50L).applicantId(100L)
                        .status(InspectionRequestStatus.PENDING)
                        .createdAt(Instant.now()).updatedAt(Instant.now()).build());

        mockMvc.perform(post("/api/inspections")
                        .with(asPrincipal(100L, Role.APPLICANT))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "slotId": 50,
                                  "notes": "I'm interested"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", is(123)))
                .andExpect(jsonPath("$.status", is("PENDING")))
                .andExpect(jsonPath("$.slotId", is(50)));
    }

    @Test
    void ownerCannotSubmitInspectionRequestProvingPreAuthorizeIsWired() throws Exception {
        // Per the PRD: owners and agents don't request inspections — applicants do.
        mockMvc.perform(post("/api/inspections")
                        .with(asPrincipal(99L, Role.OWNER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "slotId": 50 }
                                """))
                .andExpect(status().isForbidden());

        verify(inspectionService, never()).requestSlot(any(), any());
    }

    @Test
    void applicantListMineReturnsTheirBookings() throws Exception {
        when(inspectionService.listMine(eq(100L), any())).thenReturn(
                new org.springframework.data.domain.PageImpl<>(List.of(
                        InspectionRequest.builder()
                                .id(33L).slotId(12L).applicantId(100L)
                                .status(InspectionRequestStatus.PENDING)
                                .createdAt(Instant.now()).updatedAt(Instant.now())
                                .build())));

        mockMvc.perform(get("/api/inspections/mine")
                        .with(asPrincipal(100L, Role.APPLICANT)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id", is(33)))
                .andExpect(jsonPath("$.content[0].applicantId", is(100)));
    }

    @Test
    void listMineRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/inspections/mine"))
                .andExpect(status().isUnauthorized());
    }

    private static RequestPostProcessor asPrincipal(Long userId, Role role) {
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                new JwtPrincipal(userId, "x@example.com", role, 1),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role.name())));
        return authentication(auth);
    }
}
