package com.dreamhomes.haven.agentlisting;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import com.dreamhomes.haven.agentlisting.dto.AgentListingResponse;
import com.dreamhomes.haven.agentlisting.model.AgentListing;
import com.dreamhomes.haven.agentlisting.model.AgentListingStatus;

@WebMvcTest(AgentListingController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = {
        "haven.rate-limit.enabled=false",
        "cors.allowed-origins=http://localhost:3000",
        "jwt.secret=test-secret-not-a-placeholder-and-32-bytes-or-more",
        "jwt.expiration-ms=3600000",
        "jwt.issuer=test-issuer",
        "jwt.audience=test-audience"
})
class AgentListingControllerTest {

    @Autowired MockMvc mockMvc;
    @MockBean AgentListingService agentListingService;
    @MockBean JwtService jwtService;
    @MockBean UserCredentialsService userCredentialsService;

    @Test
    void ownerRequestsAssignmentReturns201() throws Exception {
        when(agentListingService.request(eq(50L), eq(7L), eq(60L)))
                .thenReturn(stub(123L, AgentListingStatus.REQUESTED));

        mockMvc.perform(post("/api/listings/7/agent-assignment")
                        .with(asPrincipal(50L, Role.OWNER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"agentId\":60}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", is(123)))
                .andExpect(jsonPath("$.status", is("REQUESTED")));
    }

    @Test
    void agentCannotRequestAssignmentProvingPreAuthorizeIsWired() throws Exception {
        mockMvc.perform(post("/api/listings/7/agent-assignment")
                        .with(asPrincipal(60L, Role.AGENT))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"agentId\":60}"))
                .andExpect(status().isForbidden());

        verify(agentListingService, never()).request(any(), any(), any());
    }

    @Test
    void agentAcceptsAssignmentReturns200WithAcceptedStatus() throws Exception {
        when(agentListingService.respond(eq(60L), eq(123L), eq(AgentListingStatus.ACCEPTED), eq(null)))
                .thenReturn(stub(123L, AgentListingStatus.ACCEPTED));

        mockMvc.perform(post("/api/agent-listings/123/accept")
                        .with(asPrincipal(60L, Role.AGENT)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("ACCEPTED")));
    }

    @Test
    void ownerCannotAcceptOnBehalfOfAgent() throws Exception {
        mockMvc.perform(post("/api/agent-listings/123/accept")
                        .with(asPrincipal(50L, Role.OWNER)))
                .andExpect(status().isForbidden());

        verify(agentListingService, never()).respond(any(), any(), any(), any());
    }

    @Test
    void agentDeclineWithReasonReturns200WithDeclinedStatus() throws Exception {
        when(agentListingService.respond(eq(60L), eq(123L), eq(AgentListingStatus.DECLINED), eq("busy")))
                .thenReturn(stub(123L, AgentListingStatus.DECLINED));

        mockMvc.perform(post("/api/agent-listings/123/decline")
                        .with(asPrincipal(60L, Role.AGENT))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"busy\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("DECLINED")));
    }

    @Test
    void declineWithoutReasonReturns400() throws Exception {
        mockMvc.perform(post("/api/agent-listings/123/decline")
                        .with(asPrincipal(60L, Role.AGENT))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"\"}"))
                .andExpect(status().isBadRequest());

        verify(agentListingService, never()).respond(any(), any(), any(), any());
    }

    @Test
    void ownerRevokesAssignmentWithReasonReturns200() throws Exception {
        when(agentListingService.revoke(eq(50L), eq(Role.OWNER), eq(123L), eq("switching")))
                .thenReturn(stub(123L, AgentListingStatus.REVOKED));

        mockMvc.perform(post("/api/agent-listings/123/revoke")
                        .with(asPrincipal(50L, Role.OWNER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"switching\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("REVOKED")));
    }

    private static AgentListingResponse fromStub(Long id, AgentListingStatus status) {
        AgentListing al = stub(id, status);
        return new AgentListingResponse(al.getId(), al.getListingId(), al.getAgentUserId(),
                al.getRequestedByOwnerId(), al.getStatus(), al.getDecisionReason(),
                al.getRequestedAt(), al.getDecidedAt());
    }

    private static AgentListing stub(Long id, AgentListingStatus status) {
        return AgentListing.builder()
                .id(id).listingId(7L).agentUserId(60L).requestedByOwnerId(50L)
                .status(status).requestedAt(Instant.now())
                .decidedAt(status == AgentListingStatus.REQUESTED ? null : Instant.now())
                .version(0L).build();
    }

    private static RequestPostProcessor asPrincipal(Long userId, Role role) {
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                new JwtPrincipal(userId, "x@example.com", role, 1),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role.name())));
        return authentication(auth);
    }
}
