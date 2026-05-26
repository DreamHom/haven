package com.dreamhomes.haven.promotion;
import com.dreamhomes.haven.auth.JwtPrincipal;
import com.dreamhomes.haven.auth.service.JwtService;
import com.dreamhomes.haven.common.config.SecurityConfig;
import com.dreamhomes.haven.promotion.dto.PromotionMetricsResponse;
import com.dreamhomes.haven.promotion.dto.PromotionMetricsSummaryResponse;
import com.dreamhomes.haven.promotion.dto.PromotionResponse;
import com.dreamhomes.haven.promotion.model.PromotionPlacement;
import com.dreamhomes.haven.promotion.model.PromotionStatus;
import com.dreamhomes.haven.promotion.model.PromotionTargetType;
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


@WebMvcTest(AdminPromotionController.class)
@Import({SecurityConfig.class, com.dreamhomes.haven.support.JwtCookieTestStubConfiguration.class})
@TestPropertySource(properties = {
        "haven.rate-limit.enabled=false",
        "cors.allowed-origins=http://localhost:3000",
        "haven.jwt.expiration-ms=3600000",
        "haven.jwt.issuer=test-issuer",
        "haven.jwt.audience=test-audience"
})
class AdminPromotionControllerTest {

    @Autowired MockMvc mockMvc;
    @MockBean PromotionService promotionService;
    @MockBean JwtService jwtService;
    @MockBean com.dreamhomes.haven.auth.blocklist.JwtBlocklistRepository jwtBlocklistRepository;
    @MockBean UserCredentialsService userCredentialsService;

    @Test
    void adminSearchPassesFiltersToService() throws Exception {
        when(promotionService.adminSearch(any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(response(12L, PromotionStatus.ACTIVE))));

        mockMvc.perform(get("/api/admin/promotions")
                        .with(asPrincipal(1L, Role.ADMIN))
                        .param("status", "ACTIVE")
                        .param("targetType", "LISTING")
                        .param("placement", "HOMEPAGE_FEATURED")
                        .param("createdByUserId", "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id", is(12)));

        verify(promotionService).adminSearch(
                eq(PromotionStatus.ACTIVE),
                eq(PromotionTargetType.LISTING),
                eq(PromotionPlacement.HOMEPAGE_FEATURED),
                eq(7L),
                any());
    }

    @Test
    void ownerCannotUseAdminPromotionEndpoints() throws Exception {
        mockMvc.perform(get("/api/admin/promotions")
                        .with(asPrincipal(7L, Role.OWNER)))
                .andExpect(status().isForbidden());

        verify(promotionService, never()).adminSearch(any(), any(), any(), any(), any());
    }

    @Test
    void adminApprovesPromotion() throws Exception {
        when(promotionService.approve(eq(1L), eq(12L), eq(9), eq("Good candidate")))
                .thenReturn(response(12L, PromotionStatus.ACTIVE));

        mockMvc.perform(post("/api/admin/promotions/12/approve")
                        .with(asPrincipal(1L, Role.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "priority": 9, "reason": "Good candidate" }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("ACTIVE")));
    }

    @Test
    void rejectRequiresReason() throws Exception {
        mockMvc.perform(post("/api/admin/promotions/12/reject")
                        .with(asPrincipal(1L, Role.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "reason": "" }
                                """))
                .andExpect(status().isBadRequest());

        verify(promotionService, never()).reject(any(), any(), any());
    }

    @Test
    void adminCanPauseResumeAndRevoke() throws Exception {
        when(promotionService.pause(1L, 12L, "Hold")).thenReturn(response(12L, PromotionStatus.PAUSED));
        when(promotionService.resume(1L, 12L, "Back")).thenReturn(response(12L, PromotionStatus.ACTIVE));
        when(promotionService.revoke(1L, 12L, "Policy")).thenReturn(response(12L, PromotionStatus.REVOKED));

        mockMvc.perform(post("/api/admin/promotions/12/pause")
                        .with(asPrincipal(1L, Role.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "reason": "Hold" }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("PAUSED")));

        mockMvc.perform(post("/api/admin/promotions/12/resume")
                        .with(asPrincipal(1L, Role.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "reason": "Back" }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("ACTIVE")));

        mockMvc.perform(post("/api/admin/promotions/12/revoke")
                        .with(asPrincipal(1L, Role.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "reason": "Policy" }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("REVOKED")));
    }

    @Test
    void adminReadsMetricsAndSummary() throws Exception {
        when(promotionService.metricsMineOrAdmin(1L, Role.ADMIN, 12L))
                .thenReturn(new PromotionMetricsResponse(12L, 1000, 80, 0.08));
        when(promotionService.adminMetricsSummary())
                .thenReturn(new PromotionMetricsSummaryResponse(3, 2000, 100, 0.05));

        mockMvc.perform(get("/api/admin/promotions/12/metrics")
                        .with(asPrincipal(1L, Role.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clicks", is(80)));

        mockMvc.perform(get("/api/admin/promotions/metrics/summary")
                        .with(asPrincipal(1L, Role.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalActivePromotions", is(3)))
                .andExpect(jsonPath("$.averageClickThroughRate", is(0.05)));
    }

    private static PromotionResponse response(Long id, PromotionStatus status) {
        Instant now = Instant.parse("2026-05-24T10:00:00Z");
        return new PromotionResponse(id, PromotionTargetType.LISTING, 44L,
                PromotionPlacement.HOMEPAGE_FEATURED, status,
                Instant.parse("2026-06-01T00:00:00Z"),
                Instant.parse("2026-06-15T00:00:00Z"),
                0, 7L, 1L, now, null, now, now);
    }

    private static RequestPostProcessor asPrincipal(Long userId, Role role) {
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                new JwtPrincipal(userId, "x@example.com", role, 1),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role.name())));
        return authentication(auth);
    }
}