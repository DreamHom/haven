package com.dreamhomes.haven.promotion;
import com.dreamhomes.haven.auth.JwtPrincipal;
import com.dreamhomes.haven.auth.service.JwtService;
import com.dreamhomes.haven.common.config.SecurityConfig;
import com.dreamhomes.haven.promotion.dto.PromotionMetricsResponse;
import com.dreamhomes.haven.promotion.dto.PromotionPublicResponse;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


@WebMvcTest(PromotionController.class)
@Import({SecurityConfig.class, com.dreamhomes.haven.support.JwtCookieTestStubConfiguration.class})
@TestPropertySource(properties = {
        "haven.rate-limit.enabled=false",
        "cors.allowed-origins=http://localhost:3000",
        "haven.jwt.expiration-ms=3600000",
        "haven.jwt.issuer=test-issuer",
        "haven.jwt.audience=test-audience"
})
class PromotionControllerTest {

    @Autowired MockMvc mockMvc;
    @MockBean PromotionService promotionService;
    @MockBean JwtService jwtService;
    @MockBean com.dreamhomes.haven.auth.blocklist.JwtBlocklistRepository jwtBlocklistRepository;
    @MockBean UserCredentialsService userCredentialsService;

    @Test
    void ownerRequestsPromotionReturns201() throws Exception {
        when(promotionService.request(eq(7L), any())).thenReturn(response(12L));

        mockMvc.perform(post("/api/promotions")
                        .with(asPrincipal(7L, Role.OWNER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "targetType": "LISTING",
                                  "targetId": 44,
                                  "placement": "HOMEPAGE_FEATURED",
                                  "startsAt": "2026-06-01T00:00:00Z",
                                  "endsAt": "2026-06-15T00:00:00Z"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", is(12)))
                .andExpect(jsonPath("$.status", is("PENDING")));
    }

    @Test
    void requestRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/promotions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "targetType": "LISTING",
                                  "targetId": 44,
                                  "placement": "HOMEPAGE_FEATURED",
                                  "startsAt": "2026-06-01T00:00:00Z",
                                  "endsAt": "2026-06-15T00:00:00Z"
                                }
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void requestValidationRejectsMissingRequiredFields() throws Exception {
        mockMvc.perform(post("/api/promotions")
                        .with(asPrincipal(7L, Role.OWNER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "targetType": "LISTING" }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void mineReturnsAuthenticatedUsersPromotions() throws Exception {
        when(promotionService.listMine(eq(7L), any()))
                .thenReturn(new PageImpl<>(List.of(response(12L))));

        mockMvc.perform(get("/api/promotions/mine")
                        .with(asPrincipal(7L, Role.OWNER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id", is(12)));
    }

    @Test
    void ownerCanReadOwnMetrics() throws Exception {
        when(promotionService.metricsMineOrAdmin(7L, Role.OWNER, 12L))
                .thenReturn(new PromotionMetricsResponse(12L, 1000, 80, 0.08));

        mockMvc.perform(get("/api/promotions/12/metrics")
                        .with(asPrincipal(7L, Role.OWNER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.impressions", is(1000)))
                .andExpect(jsonPath("$.clickThroughRate", is(0.08)));
    }

    @Test
    void homepageFeaturedIsPublicAndUsesDedicatedPlacement() throws Exception {
        when(promotionService.publicFor(eq(PromotionPlacement.HOMEPAGE_FEATURED), any()))
                .thenReturn(new PageImpl<>(List.of(publicListing())));

        mockMvc.perform(get("/api/promotions/homepage-featured"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].promotionId", is(12)))
                .andExpect(jsonPath("$.content[0].label", is("Featured")));
    }

    @Test
    void anonymousImpressionIsAcceptedAndStoresNullViewer() throws Exception {
        mockMvc.perform(post("/api/promotions/12/impression")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "placement": "HOMEPAGE_FEATURED" }
                                """))
                .andExpect(status().isNoContent());

        verify(promotionService).recordImpression(12L, PromotionPlacement.HOMEPAGE_FEATURED, null);
    }

    @Test
    void authenticatedClickPassesViewerUserId() throws Exception {
        mockMvc.perform(post("/api/promotions/12/click")
                        .with(asPrincipal(7L, Role.APPLICANT))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "placement": "HOMEPAGE_FEATURED" }
                                """))
                .andExpect(status().isNoContent());

        verify(promotionService).recordClick(12L, PromotionPlacement.HOMEPAGE_FEATURED, 7L);
    }

    private static PromotionResponse response(Long id) {
        Instant now = Instant.parse("2026-05-24T10:00:00Z");
        return new PromotionResponse(id, PromotionTargetType.LISTING, 44L,
                PromotionPlacement.HOMEPAGE_FEATURED, PromotionStatus.PENDING,
                Instant.parse("2026-06-01T00:00:00Z"),
                Instant.parse("2026-06-15T00:00:00Z"),
                0, 7L, null, null, null, now, now);
    }

    private static PromotionPublicResponse publicListing() {
        return new PromotionPublicResponse(12L, PromotionTargetType.LISTING, 44L,
                PromotionPlacement.HOMEPAGE_FEATURED, "Featured", null, null);
    }

    private static RequestPostProcessor asPrincipal(Long userId, Role role) {
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                new JwtPrincipal(userId, "x@example.com", role, 1),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role.name())));
        return authentication(auth);
    }
}