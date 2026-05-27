package com.dreamhomes.haven.photo;

import com.dreamhomes.haven.auth.JwtPrincipal;
import com.dreamhomes.haven.auth.service.JwtService;
import com.dreamhomes.haven.common.config.SecurityConfig;
import com.dreamhomes.haven.photo.dto.PhotoUploadUrlResponse;
import com.dreamhomes.haven.photo.exception.PhotoUploadContentTypeNotAllowedException;
import com.dreamhomes.haven.photo.exception.PhotoUploadIntentAlreadyConfirmedException;
import com.dreamhomes.haven.photo.exception.PhotoUploadIntentExpiredException;
import com.dreamhomes.haven.photo.exception.PhotoUploadIntentForeignCallerException;
import com.dreamhomes.haven.photo.exception.PhotoUploadIntentNotFoundException;
import com.dreamhomes.haven.photo.exception.PhotoUploadObjectMissingException;
import com.dreamhomes.haven.photo.exception.PhotoUploadSizeMismatchException;
import com.dreamhomes.haven.photo.exception.PhotoUploadSizeOutOfBoundsException;
import com.dreamhomes.haven.photo.storage.PhotoStorage;
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

import static org.hamcrest.Matchers.containsString;
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

@WebMvcTest(ListingPhotoController.class)
@Import({SecurityConfig.class, com.dreamhomes.haven.support.JwtCookieTestStubConfiguration.class,
        com.dreamhomes.haven.photo.ListingPhotoMapperImpl.class})
@TestPropertySource(properties = {
        "haven.rate-limit.enabled=false",
        "cors.allowed-origins=http://localhost:3000",
        "haven.jwt.expiration-ms=3600000",
        "haven.jwt.issuer=test-issuer",
        "haven.jwt.audience=test-audience"
})
class ListingPhotoUploadIntentControllerTest {

    @Autowired MockMvc mockMvc;
    @MockBean ListingPhotoService listingPhotoService;
    @MockBean ListingPhotoUploadIntentService uploadIntentService;
    @MockBean PhotoStorage photoStorage;
    @MockBean JwtService jwtService;
    @MockBean com.dreamhomes.haven.auth.blocklist.JwtBlocklistRepository jwtBlocklistRepository;
    @MockBean UserCredentialsService userCredentialsService;

    // ---------- POST /upload-url ----------

    @Test
    void ownerMintingUploadUrlReturns201() throws Exception {
        when(uploadIntentService.createIntent(eq(50L), eq(17L), any()))
                .thenReturn(new PhotoUploadUrlResponse(
                        "https://r2/listings/17/abc.jpg?sig=x",
                        "listings/17/abc.jpg",
                        Instant.parse("2026-05-24T10:00:00Z"),
                        10_485_760L,
                        List.of("image/jpeg", "image/png", "image/webp")));

        mockMvc.perform(post("/api/listings/17/photos/upload-url")
                        .with(asPrincipal(50L, Role.OWNER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contentType\":\"image/jpeg\",\"sizeBytes\":1024,\"originalFilename\":\"hero.jpg\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.uploadUrl", containsString("listings/17/abc.jpg")))
                .andExpect(jsonPath("$.fileKey", is("listings/17/abc.jpg")))
                .andExpect(jsonPath("$.maxSizeBytes", is(10485760)));
    }

    @Test
    void mintingWithoutAuthReturns401() throws Exception {
        mockMvc.perform(post("/api/listings/17/photos/upload-url")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contentType\":\"image/jpeg\",\"sizeBytes\":1024}"))
                .andExpect(status().isUnauthorized());

        verify(uploadIntentService, never()).createIntent(any(), any(), any());
    }

    @Test
    void mintingAsApplicantReturns403() throws Exception {
        mockMvc.perform(post("/api/listings/17/photos/upload-url")
                        .with(asPrincipal(99L, Role.APPLICANT))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contentType\":\"image/jpeg\",\"sizeBytes\":1024}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void disallowedContentTypeIs400() throws Exception {
        when(uploadIntentService.createIntent(any(), any(), any()))
                .thenThrow(new PhotoUploadContentTypeNotAllowedException("application/pdf"));

        mockMvc.perform(post("/api/listings/17/photos/upload-url")
                        .with(asPrincipal(50L, Role.OWNER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contentType\":\"application/pdf\",\"sizeBytes\":1024}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void sizeOutOfBoundsIs400() throws Exception {
        when(uploadIntentService.createIntent(any(), any(), any()))
                .thenThrow(new PhotoUploadSizeOutOfBoundsException(20_000_000L, 10_485_760L));

        mockMvc.perform(post("/api/listings/17/photos/upload-url")
                        .with(asPrincipal(50L, Role.OWNER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contentType\":\"image/jpeg\",\"sizeBytes\":20000000}"))
                .andExpect(status().isBadRequest());
    }

    // ---------- POST /confirm ----------

    @Test
    void confirmingForeignFileKeyIs403() throws Exception {
        when(uploadIntentService.confirm(any(), any(), any()))
                .thenThrow(new PhotoUploadIntentForeignCallerException());

        mockMvc.perform(post("/api/listings/17/photos/confirm")
                        .with(asPrincipal(50L, Role.OWNER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fileKey\":\"listings/17/abc.jpg\",\"contentType\":\"image/jpeg\",\"sizeBytes\":1024}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void confirmingUnknownFileKeyIs409() throws Exception {
        when(uploadIntentService.confirm(any(), any(), any()))
                .thenThrow(new PhotoUploadIntentNotFoundException());

        mockMvc.perform(post("/api/listings/17/photos/confirm")
                        .with(asPrincipal(50L, Role.OWNER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fileKey\":\"ghost\",\"contentType\":\"image/jpeg\",\"sizeBytes\":1024}"))
                .andExpect(status().isConflict());
    }

    @Test
    void confirmingAlreadyConfirmedIs409() throws Exception {
        when(uploadIntentService.confirm(any(), any(), any()))
                .thenThrow(new PhotoUploadIntentAlreadyConfirmedException());

        mockMvc.perform(post("/api/listings/17/photos/confirm")
                        .with(asPrincipal(50L, Role.OWNER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fileKey\":\"listings/17/abc.jpg\",\"contentType\":\"image/jpeg\",\"sizeBytes\":1024}"))
                .andExpect(status().isConflict());
    }

    @Test
    void confirmingExpiredIs409() throws Exception {
        when(uploadIntentService.confirm(any(), any(), any()))
                .thenThrow(new PhotoUploadIntentExpiredException());

        mockMvc.perform(post("/api/listings/17/photos/confirm")
                        .with(asPrincipal(50L, Role.OWNER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fileKey\":\"listings/17/abc.jpg\",\"contentType\":\"image/jpeg\",\"sizeBytes\":1024}"))
                .andExpect(status().isConflict());
    }

    @Test
    void confirmingMissingObjectIs422() throws Exception {
        when(uploadIntentService.confirm(any(), any(), any()))
                .thenThrow(new PhotoUploadObjectMissingException("listings/17/abc.jpg"));

        mockMvc.perform(post("/api/listings/17/photos/confirm")
                        .with(asPrincipal(50L, Role.OWNER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fileKey\":\"listings/17/abc.jpg\",\"contentType\":\"image/jpeg\",\"sizeBytes\":1024}"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void confirmingSizeMismatchIs422() throws Exception {
        when(uploadIntentService.confirm(any(), any(), any()))
                .thenThrow(new PhotoUploadSizeMismatchException(1024L, 2048L));

        mockMvc.perform(post("/api/listings/17/photos/confirm")
                        .with(asPrincipal(50L, Role.OWNER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fileKey\":\"listings/17/abc.jpg\",\"contentType\":\"image/jpeg\",\"sizeBytes\":1024}"))
                .andExpect(status().isUnprocessableEntity());
    }

    private static RequestPostProcessor asPrincipal(Long userId, Role role) {
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                new JwtPrincipal(userId, "x@example.com", role, 1),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role.name())));
        return authentication(auth);
    }
}
