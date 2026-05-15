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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import com.dreamhomes.haven.inspection.dto.RequestInspectionCommand;
import com.dreamhomes.haven.inspection.model.InspectionRequest;
import com.dreamhomes.haven.inspection.model.InspectionRequestStatus;
import com.dreamhomes.haven.inspection.controller.InspectionController;
import com.dreamhomes.haven.inspection.service.InspectionService;

@WebMvcTest(InspectionController.class)
@Import({SecurityConfig.class, com.dreamhomes.haven.support.JwtCookieTestStubConfiguration.class})
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
    @MockBean com.dreamhomes.haven.auth.blocklist.JwtBlocklistRepository jwtBlocklistRepository;
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

    @Test
    void applicantCanCancelTheirPendingInspection() throws Exception {
        when(inspectionService.cancel(eq(100L), eq(33L)))
                .thenReturn(InspectionRequest.builder().id(33L).build());

        mockMvc.perform(delete("/api/inspections/33")
                        .with(asPrincipal(100L, Role.APPLICANT)))
                .andExpect(status().isNoContent());

        verify(inspectionService).cancel(100L, 33L);
    }

    @Test
    void cancelRequiresApplicantRole() throws Exception {
        mockMvc.perform(delete("/api/inspections/33")
                        .with(asPrincipal(99L, Role.OWNER)))
                .andExpect(status().isForbidden());

        verify(inspectionService, never()).cancel(any(), any());
    }

    @Test
    void agentRescheduleReturns200WithUpdatedSlot() throws Exception {
        when(inspectionService.rescheduleApprovedByAgent(eq(50L), eq(10L), eq(60L)))
                .thenReturn(InspectionRequest.builder()
                        .id(10L).slotId(60L).applicantId(2L)
                        .status(InspectionRequestStatus.APPROVED)
                        .notes("n").agentExtras(null)
                        .createdAt(Instant.now()).updatedAt(Instant.now())
                        .build());

        mockMvc.perform(post("/api/inspections/10/agent/reschedule")
                        .with(asPrincipal(50L, Role.AGENT))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"slotId\": 60 }"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slotId", is(60)))
                .andExpect(jsonPath("$.status", is("APPROVED")));

        verify(inspectionService).rescheduleApprovedByAgent(50L, 10L, 60L);
    }

    @Test
    void ownerCannotCallAgentReschedule() throws Exception {
        mockMvc.perform(post("/api/inspections/10/agent/reschedule")
                        .with(asPrincipal(99L, Role.OWNER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"slotId\": 60 }"))
                .andExpect(status().isForbidden());

        verify(inspectionService, never()).rescheduleApprovedByAgent(anyLong(), anyLong(), anyLong());
    }

    @Test
    void agentPatchExtrasReturns200() throws Exception {
        when(inspectionService.patchAgentExtras(eq(50L), eq(10L), eq("Meet at side gate")))
                .thenReturn(InspectionRequest.builder()
                        .id(10L).slotId(1L).applicantId(2L)
                        .status(InspectionRequestStatus.APPROVED)
                        .agentExtras("Meet at side gate")
                        .createdAt(Instant.now()).updatedAt(Instant.now())
                        .build());

        mockMvc.perform(patch("/api/inspections/10/agent/extras")
                        .with(asPrincipal(50L, Role.AGENT))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"extras\": \"Meet at side gate\" }"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.agentExtras", is("Meet at side gate")));

        verify(inspectionService).patchAgentExtras(50L, 10L, "Meet at side gate");
    }

    private static RequestPostProcessor asPrincipal(Long userId, Role role) {
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                new JwtPrincipal(userId, "x@example.com", role, 1),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role.name())));
        return authentication(auth);
    }
}
