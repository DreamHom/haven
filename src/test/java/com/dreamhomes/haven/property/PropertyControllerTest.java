package com.dreamhomes.haven.property;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import com.dreamhomes.haven.property.dto.CreatePropertyCommand;
import com.dreamhomes.haven.property.exception.InvalidPropertyForTypeException;
import com.dreamhomes.haven.property.model.Property;
import com.dreamhomes.haven.property.model.PropertyType;

/**
 * Covers the controller-layer contract for property creation:
 * <ul>
 *   <li>OWNER-authenticated request maps the body to a CreatePropertyCommand and returns 201.</li>
 *   <li>Service-thrown {@link InvalidPropertyForTypeException} surfaces as 400.</li>
 *   <li>The endpoint is wired with @PreAuthorize("hasRole('OWNER')") — an APPLICANT-roled
 *       caller is rejected. Spring enforces the rule; this test proves the annotation is in place.</li>
 * </ul>
 */
@WebMvcTest(PropertyController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = {
        "haven.rate-limit.enabled=false",
        "cors.allowed-origins=http://localhost:3000",
        "haven.jwt.expiration-ms=3600000",
        "haven.jwt.issuer=test-issuer",
        "haven.jwt.audience=test-audience"
})
class PropertyControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    PropertyService propertyService;

    @MockBean
    JwtService jwtService;

    @MockBean
    com.dreamhomes.haven.auth.blocklist.JwtBlocklistRepository jwtBlocklistRepository;

    @MockBean
    UserCredentialsService userCredentialsService;

    @Test
    void ownerCreatingApartmentReturns201WithPropertySummary() throws Exception {
        when(propertyService.create(eq(99L), any(CreatePropertyCommand.class)))
                .thenAnswer(inv -> Property.builder()
                        .id(7L)
                        .ownerId(99L)
                        .type(PropertyType.APARTMENT)
                        .address("12 Lekki Phase 1, Lagos")
                        .bedrooms(3)
                        .bathrooms(2)
                        .sizeSqm(new BigDecimal("128.50"))
                        .description("Top floor")
                        .createdAt(Instant.now())
                        .build());

        mockMvc.perform(post("/api/properties")
                        .with(asPrincipal(99L, Role.OWNER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "type": "APARTMENT",
                                  "address": "12 Lekki Phase 1, Lagos",
                                  "bedrooms": 3,
                                  "bathrooms": 2,
                                  "sizeSqm": 128.50,
                                  "description": "Top floor"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", is(7)))
                .andExpect(jsonPath("$.ownerId", is(99)))
                .andExpect(jsonPath("$.type", is("APARTMENT")))
                .andExpect(jsonPath("$.bedrooms", is(3)));
    }

    @Test
    void invalidPropertyForTypeExceptionFromServiceMapsTo400() throws Exception {
        when(propertyService.create(eq(99L), any(CreatePropertyCommand.class)))
                .thenThrow(new InvalidPropertyForTypeException("APARTMENT requires bedrooms"));

        mockMvc.perform(post("/api/properties")
                        .with(asPrincipal(99L, Role.OWNER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "type": "APARTMENT",
                                  "address": "Some address"
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void ownerListMineReturnsTheirProperties() throws Exception {
        when(propertyService.listMine(eq(99L), any())).thenReturn(
                new org.springframework.data.domain.PageImpl<>(List.of(
                        new com.dreamhomes.haven.property.dto.PropertyResponse(
                                7L, 99L, PropertyType.APARTMENT,
                                "12 Lekki Phase 1, Lagos", 3, 2, new BigDecimal("128.50"),
                                "Top floor", Instant.now()))));

        mockMvc.perform(get("/api/properties/mine")
                        .with(asPrincipal(99L, Role.OWNER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id", is(7)))
                .andExpect(jsonPath("$.content[0].ownerId", is(99)));
    }

    @Test
    void getByIdReturnsPropertyWhenCallerOwnsIt() throws Exception {
        when(propertyService.ownerOf(7L))
                .thenReturn(java.util.Optional.of(99L));
        when(propertyService.findById(7L)).thenReturn(
                new com.dreamhomes.haven.property.dto.PropertyResponse(
                        7L, 99L, PropertyType.APARTMENT,
                        "12 Lekki Phase 1, Lagos", 3, 2, new BigDecimal("128.50"),
                        "Top floor", Instant.now()));

        mockMvc.perform(get("/api/properties/7")
                        .with(asPrincipal(99L, Role.OWNER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(7)));
    }

    @Test
    void getByIdReturns404WhenCallerIsNotOwner() throws Exception {
        when(propertyService.ownerOf(7L))
                .thenReturn(java.util.Optional.of(99L));

        mockMvc.perform(get("/api/properties/7")
                        .with(asPrincipal(50L, Role.OWNER)))
                .andExpect(status().isNotFound());
    }

    @Test
    void getByIdReturns404WhenPropertyMissing() throws Exception {
        when(propertyService.ownerOf(404L))
                .thenReturn(java.util.Optional.empty());

        mockMvc.perform(get("/api/properties/404")
                        .with(asPrincipal(99L, Role.OWNER)))
                .andExpect(status().isNotFound());
    }

    @Test
    void applicantRoleIsRejectedWith403ProvingPreAuthorizeIsWired() throws Exception {
        mockMvc.perform(post("/api/properties")
                        .with(asPrincipal(50L, Role.APPLICANT))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "type": "APARTMENT",
                                  "address": "12 Lekki Phase 1, Lagos",
                                  "bedrooms": 3,
                                  "bathrooms": 2
                                }
                                """))
                .andExpect(status().isForbidden());

        verify(propertyService, never()).create(any(), any());
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor asPrincipal(Long userId, Role role) {
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                new JwtPrincipal(userId, "x@example.com", role, 1),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role.name())));
        return authentication(auth);
    }
}
