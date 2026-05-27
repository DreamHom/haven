package com.dreamhomes.haven.photo;

import com.dreamhomes.haven.agentlisting.AgentListingRepository;
import com.dreamhomes.haven.agentlisting.model.AgentListingStatus;
import com.dreamhomes.haven.listing.ListingService;
import com.dreamhomes.haven.listing.exception.ListingNotFoundException;
import com.dreamhomes.haven.listing.exception.NotPropertyOwnerException;
import com.dreamhomes.haven.photo.dto.PhotoConfirmRequest;
import com.dreamhomes.haven.photo.dto.PhotoUploadUrlRequest;
import com.dreamhomes.haven.photo.dto.PhotoUploadUrlResponse;
import com.dreamhomes.haven.photo.exception.PhotoUploadContentTypeNotAllowedException;
import com.dreamhomes.haven.photo.exception.PhotoUploadIntentAlreadyConfirmedException;
import com.dreamhomes.haven.photo.exception.PhotoUploadIntentExpiredException;
import com.dreamhomes.haven.photo.exception.PhotoUploadIntentForeignCallerException;
import com.dreamhomes.haven.photo.exception.PhotoUploadIntentNotFoundException;
import com.dreamhomes.haven.photo.exception.PhotoUploadObjectMissingException;
import com.dreamhomes.haven.photo.exception.PhotoUploadSizeMismatchException;
import com.dreamhomes.haven.photo.exception.PhotoUploadSizeOutOfBoundsException;
import com.dreamhomes.haven.photo.storage.PhotoPresignedStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ListingPhotoUploadIntentServiceTest {

    @Mock PhotoUploadIntentRepository intentRepository;
    @Mock ListingPhotoRepository photoRepository;
    @Mock ListingService listingService;
    @Mock AgentListingRepository agentListingRepository;
    @Mock PhotoPresignedStorage presignedStorage;

    ListingPhotoUploadIntentService service;
    Instant now = Instant.parse("2026-05-24T08:00:00Z");

    @BeforeEach
    void setUp() {
        service = new ListingPhotoUploadIntentService(intentRepository, photoRepository,
                listingService, agentListingRepository, presignedStorage);
        service.setClock(Clock.fixed(now, ZoneOffset.UTC));
    }

    // ---------- createIntent ----------

    @Test
    void ownerMintsUrlAndPersistsIntent() {
        when(listingService.ownerOf(17L)).thenReturn(Optional.of(50L));
        when(presignedStorage.presignUpload(anyString(), anyString(), anyLong(), any()))
                .thenReturn(URI.create("https://r2.example.com/listings/17/abc.jpg?X-Amz-Sig=x"));

        PhotoUploadUrlResponse resp = service.createIntent(50L, 17L, new PhotoUploadUrlRequest(
                "image/jpeg", 1024L, "front.jpg"));

        assertThat(resp.uploadUrl()).startsWith("https://r2.example.com/");
        assertThat(resp.fileKey()).startsWith("listings/17/").endsWith(".jpg");
        assertThat(resp.maxSizeBytes()).isEqualTo(ListingPhotoUploadIntentService.MAX_SIZE_BYTES);
        assertThat(resp.allowedContentTypes()).containsExactly("image/jpeg", "image/png", "image/webp");
        assertThat(resp.expiresAt()).isEqualTo(now.plus(Duration.ofMinutes(10)));

        ArgumentCaptor<PhotoUploadIntent> cap = ArgumentCaptor.forClass(PhotoUploadIntent.class);
        verify(intentRepository).save(cap.capture());
        PhotoUploadIntent saved = cap.getValue();
        assertThat(saved.getListingId()).isEqualTo(17L);
        assertThat(saved.getRequestedBy()).isEqualTo(50L);
        assertThat(saved.getContentType()).isEqualTo("image/jpeg");
        assertThat(saved.getMaxSizeBytes()).isEqualTo(1024L);
        assertThat(saved.getConfirmedAt()).isNull();
        assertThat(saved.getFileKey()).isEqualTo(resp.fileKey());
    }

    @Test
    void activeAgentCanMintUrl() {
        when(listingService.ownerOf(17L)).thenReturn(Optional.of(50L));
        when(agentListingRepository.existsByListingIdAndAgentUserIdAndStatus(
                17L, 77L, AgentListingStatus.ACCEPTED)).thenReturn(true);
        when(presignedStorage.presignUpload(anyString(), anyString(), anyLong(), any()))
                .thenReturn(URI.create("https://r2.example.com/x"));

        service.createIntent(77L, 17L, new PhotoUploadUrlRequest("image/png", 2048L, null));

        verify(intentRepository).save(any());
    }

    @Test
    void nonOwnerNonAgentRejected() {
        when(listingService.ownerOf(17L)).thenReturn(Optional.of(50L));
        when(agentListingRepository.existsByListingIdAndAgentUserIdAndStatus(
                17L, 99L, AgentListingStatus.ACCEPTED)).thenReturn(false);

        assertThatThrownBy(() -> service.createIntent(99L, 17L, new PhotoUploadUrlRequest(
                "image/jpeg", 1024L, null)))
                .isInstanceOf(NotPropertyOwnerException.class);

        verify(intentRepository, never()).save(any());
    }

    @Test
    void missingListingIs404() {
        when(listingService.ownerOf(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createIntent(50L, 404L, new PhotoUploadUrlRequest(
                "image/jpeg", 1024L, null)))
                .isInstanceOf(ListingNotFoundException.class);
    }

    @Test
    void disallowedContentTypeRejected() {
        when(listingService.ownerOf(17L)).thenReturn(Optional.of(50L));

        assertThatThrownBy(() -> service.createIntent(50L, 17L, new PhotoUploadUrlRequest(
                "application/pdf", 1024L, null)))
                .isInstanceOf(PhotoUploadContentTypeNotAllowedException.class);

        verify(intentRepository, never()).save(any());
    }

    @Test
    void oversizedSizeRejected() {
        when(listingService.ownerOf(17L)).thenReturn(Optional.of(50L));

        assertThatThrownBy(() -> service.createIntent(50L, 17L, new PhotoUploadUrlRequest(
                "image/jpeg", ListingPhotoUploadIntentService.MAX_SIZE_BYTES + 1, null)))
                .isInstanceOf(PhotoUploadSizeOutOfBoundsException.class);

        verify(intentRepository, never()).save(any());
    }

    // ---------- confirm ----------

    @Test
    void confirmCreatesPhotoAndMarksIntentConsumed() {
        PhotoUploadIntent intent = baseIntent("listings/17/abc.jpg", 50L, 17L, now, 1024L);
        when(listingService.ownerOf(17L)).thenReturn(Optional.of(50L));
        when(intentRepository.findByFileKey(intent.getFileKey())).thenReturn(Optional.of(intent));
        when(presignedStorage.headObject(intent.getFileKey()))
                .thenReturn(PhotoPresignedStorage.HeadResult.of(1024L, "image/jpeg"));
        when(presignedStorage.publicUrlFor(intent.getFileKey()))
                .thenReturn("https://media.dreamhomes.com/listings/17/abc.jpg");
        when(photoRepository.findMaxDisplayOrderForListing(17L)).thenReturn(2);
        when(photoRepository.save(any(ListingPhoto.class))).thenAnswer(inv -> {
            ListingPhoto p = inv.getArgument(0);
            p.setId(88L);
            return p;
        });

        ListingPhoto saved = service.confirm(50L, 17L, new PhotoConfirmRequest(
                intent.getFileKey(), "image/jpeg", 1024L, 1920, 1280, "Hero"));

        assertThat(saved.getId()).isEqualTo(88L);
        assertThat(saved.getDisplayOrder()).isEqualTo(3);
        assertThat(saved.getCaption()).isEqualTo("Hero");
        assertThat(saved.getUrl()).isEqualTo("https://media.dreamhomes.com/listings/17/abc.jpg");

        ArgumentCaptor<PhotoUploadIntent> cap = ArgumentCaptor.forClass(PhotoUploadIntent.class);
        verify(intentRepository).save(cap.capture());
        assertThat(cap.getValue().getConfirmedAt()).isEqualTo(now);
        assertThat(cap.getValue().getConfirmedPhotoId()).isEqualTo(88L);
    }

    @Test
    void confirmRejectsForeignCallerOnIntent() {
        // Owner is 50, intent was issued to 77 (some agent). Caller 50 cannot confirm 77's intent.
        PhotoUploadIntent intent = baseIntent("listings/17/abc.jpg", 77L, 17L, now, 1024L);
        when(listingService.ownerOf(17L)).thenReturn(Optional.of(50L));
        when(intentRepository.findByFileKey(intent.getFileKey())).thenReturn(Optional.of(intent));

        assertThatThrownBy(() -> service.confirm(50L, 17L, new PhotoConfirmRequest(
                intent.getFileKey(), "image/jpeg", 1024L, null, null, null)))
                .isInstanceOf(PhotoUploadIntentForeignCallerException.class);

        verify(photoRepository, never()).save(any());
    }

    @Test
    void confirmRejectsDoubleConfirm() {
        PhotoUploadIntent intent = baseIntent("listings/17/abc.jpg", 50L, 17L, now, 1024L);
        intent.setConfirmedAt(now.minusSeconds(60));
        when(listingService.ownerOf(17L)).thenReturn(Optional.of(50L));
        when(intentRepository.findByFileKey(intent.getFileKey())).thenReturn(Optional.of(intent));

        assertThatThrownBy(() -> service.confirm(50L, 17L, new PhotoConfirmRequest(
                intent.getFileKey(), "image/jpeg", 1024L, null, null, null)))
                .isInstanceOf(PhotoUploadIntentAlreadyConfirmedException.class);
    }

    @Test
    void confirmRejectsUnknownFileKey() {
        when(listingService.ownerOf(17L)).thenReturn(Optional.of(50L));
        when(intentRepository.findByFileKey("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.confirm(50L, 17L, new PhotoConfirmRequest(
                "ghost", "image/jpeg", 1024L, null, null, null)))
                .isInstanceOf(PhotoUploadIntentNotFoundException.class);
    }

    @Test
    void confirmRejectsExpiredIntent() {
        PhotoUploadIntent intent = baseIntent("listings/17/abc.jpg", 50L, 17L, now, 1024L);
        intent.setExpiresAt(now.minusSeconds(1));
        when(listingService.ownerOf(17L)).thenReturn(Optional.of(50L));
        when(intentRepository.findByFileKey(intent.getFileKey())).thenReturn(Optional.of(intent));

        assertThatThrownBy(() -> service.confirm(50L, 17L, new PhotoConfirmRequest(
                intent.getFileKey(), "image/jpeg", 1024L, null, null, null)))
                .isInstanceOf(PhotoUploadIntentExpiredException.class);
    }

    @Test
    void confirmRejectsMissingObjectInR2() {
        PhotoUploadIntent intent = baseIntent("listings/17/abc.jpg", 50L, 17L, now, 1024L);
        when(listingService.ownerOf(17L)).thenReturn(Optional.of(50L));
        when(intentRepository.findByFileKey(intent.getFileKey())).thenReturn(Optional.of(intent));
        when(presignedStorage.headObject(intent.getFileKey()))
                .thenReturn(PhotoPresignedStorage.HeadResult.missing());

        assertThatThrownBy(() -> service.confirm(50L, 17L, new PhotoConfirmRequest(
                intent.getFileKey(), "image/jpeg", 1024L, null, null, null)))
                .isInstanceOf(PhotoUploadObjectMissingException.class);

        verify(photoRepository, never()).save(any());
    }

    @Test
    void confirmRejectsSizeMismatch() {
        PhotoUploadIntent intent = baseIntent("listings/17/abc.jpg", 50L, 17L, now, 1024L);
        when(listingService.ownerOf(17L)).thenReturn(Optional.of(50L));
        when(intentRepository.findByFileKey(intent.getFileKey())).thenReturn(Optional.of(intent));
        when(presignedStorage.headObject(intent.getFileKey()))
                .thenReturn(PhotoPresignedStorage.HeadResult.of(2048L, "image/jpeg"));

        assertThatThrownBy(() -> service.confirm(50L, 17L, new PhotoConfirmRequest(
                intent.getFileKey(), "image/jpeg", 1024L, null, null, null)))
                .isInstanceOf(PhotoUploadSizeMismatchException.class);
    }

    // ---------- cleanup ----------

    @Test
    void cleanupDeletesRowsOlderThan24Hours() {
        when(intentRepository.deleteCreatedBefore(any())).thenReturn(3);

        service.cleanupExpiredIntents();

        ArgumentCaptor<Instant> cap = ArgumentCaptor.forClass(Instant.class);
        verify(intentRepository).deleteCreatedBefore(cap.capture());
        assertThat(cap.getValue()).isEqualTo(now.minus(Duration.ofHours(24)));
    }

    // ---------- slug helper ----------

    @Test
    void slugifyStripsPathAndExtensionAndPunctuation() {
        assertThat(ListingPhotoUploadIntentService.slugify("Living Room (final).JPG"))
                .isEqualTo("living-room-final");
        assertThat(ListingPhotoUploadIntentService.slugify(null)).isEmpty();
        assertThat(ListingPhotoUploadIntentService.slugify("../etc/passwd"))
                .isEqualTo("passwd");
    }

    // ---------- helpers ----------

    private PhotoUploadIntent baseIntent(String key, Long requestedBy, Long listingId,
                                         Instant createdAt, long maxBytes) {
        return PhotoUploadIntent.builder()
                .id(1L)
                .listingId(listingId)
                .requestedBy(requestedBy)
                .fileKey(key)
                .contentType("image/jpeg")
                .maxSizeBytes(maxBytes)
                .expiresAt(createdAt.plus(Duration.ofMinutes(10)))
                .createdAt(createdAt)
                .build();
    }
}
