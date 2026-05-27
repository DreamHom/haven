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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Item 2 — server side of the browser-direct upload dance.
 *
 * <p>Two endpoints back this service:
 * <ul>
 *   <li>{@link #createIntent} — auth check, mint a 10-min pre-signed PUT URL via
 *       {@link PhotoPresignedStorage}, persist a {@link PhotoUploadIntent} row.</li>
 *   <li>{@link #confirm} — validate the intent (owner + listing match, not expired,
 *       not already used), HEAD R2 to verify the upload landed at the expected size,
 *       then write the {@code listing_photos} row and stamp the intent confirmed.</li>
 * </ul>
 *
 * <p>The existing multipart-proxy endpoint ({@link ListingPhotoService#add}) keeps
 * working in parallel — Vista migrates gradually, both paths coexist.</p>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ListingPhotoUploadIntentService {

    /** R2 / S3 best practice keeps pre-signed URLs as short-lived as the client UX tolerates. */
    public static final Duration DEFAULT_TTL = Duration.ofMinutes(10);

    /** Capstone limit; matches the docs Vista renders next to the file picker. */
    public static final long MAX_SIZE_BYTES = 10L * 1024 * 1024; // 10 MiB

    /** Allowed content types. Any other input is 400 — short-circuits before we mint a URL. */
    public static final Set<String> ALLOWED_CONTENT_TYPES =
            Set.of("image/jpeg", "image/png", "image/webp");

    public static final List<String> ALLOWED_CONTENT_TYPES_LIST =
            List.of("image/jpeg", "image/png", "image/webp");

    private final PhotoUploadIntentRepository intentRepository;
    private final ListingPhotoRepository photoRepository;
    private final ListingService listingService;
    private final AgentListingRepository agentListingRepository;
    private final PhotoPresignedStorage presignedStorage;

    private Clock clock = Clock.systemUTC();

    /** Test seam — production uses {@link Clock#systemUTC()}. */
    void setClock(Clock clock) {
        this.clock = clock;
    }

    @Value("${haven.photos.upload-intent.ttl-seconds:600}")
    private long ttlSeconds = DEFAULT_TTL.toSeconds();

    @Transactional
    public PhotoUploadUrlResponse createIntent(Long callerId, Long listingId,
                                               PhotoUploadUrlRequest req) {
        validateAuth(callerId, listingId);

        String contentType = req.contentType() == null ? "" : req.contentType().toLowerCase(Locale.ROOT);
        if (!ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new PhotoUploadContentTypeNotAllowedException(req.contentType());
        }
        if (req.sizeBytes() <= 0 || req.sizeBytes() > MAX_SIZE_BYTES) {
            throw new PhotoUploadSizeOutOfBoundsException(req.sizeBytes(), MAX_SIZE_BYTES);
        }

        String fileKey = buildFileKey(listingId, contentType, req.originalFilename());
        Duration ttl = Duration.ofSeconds(ttlSeconds);
        Instant now = clock.instant();
        Instant expiresAt = now.plus(ttl);

        URI uploadUrl = presignedStorage.presignUpload(fileKey, contentType, req.sizeBytes(), ttl);
        if (uploadUrl == null) {
            throw new IllegalStateException("PhotoPresignedStorage returned a null URL for " + fileKey);
        }

        intentRepository.save(PhotoUploadIntent.builder()
                .listingId(listingId)
                .requestedBy(callerId)
                .fileKey(fileKey)
                .contentType(contentType)
                .maxSizeBytes(req.sizeBytes())
                .expiresAt(expiresAt)
                .createdAt(now)
                .build());

        log.info("Photo upload intent: listing={} caller={} key={} expiresAt={}",
                listingId, callerId, fileKey, expiresAt);

        return new PhotoUploadUrlResponse(uploadUrl.toString(), fileKey, expiresAt,
                MAX_SIZE_BYTES, ALLOWED_CONTENT_TYPES_LIST);
    }

    @Transactional
    public ListingPhoto confirm(Long callerId, Long listingId, PhotoConfirmRequest req) {
        validateAuth(callerId, listingId);

        PhotoUploadIntent intent = intentRepository.findByFileKey(req.fileKey())
                .orElseThrow(PhotoUploadIntentNotFoundException::new);

        if (!intent.getListingId().equals(listingId)
                || !intent.getRequestedBy().equals(callerId)) {
            throw new PhotoUploadIntentForeignCallerException();
        }
        if (intent.getConfirmedAt() != null) {
            throw new PhotoUploadIntentAlreadyConfirmedException();
        }
        if (intent.getExpiresAt().isBefore(clock.instant())) {
            throw new PhotoUploadIntentExpiredException();
        }

        PhotoPresignedStorage.HeadResult head = presignedStorage.headObject(intent.getFileKey());
        if (!head.exists()) {
            throw new PhotoUploadObjectMissingException(intent.getFileKey());
        }
        if (head.sizeBytes() == null || head.sizeBytes() != req.sizeBytes()) {
            throw new PhotoUploadSizeMismatchException(req.sizeBytes(),
                    head.sizeBytes() == null ? -1L : head.sizeBytes());
        }

        Integer currentMax = photoRepository.findMaxDisplayOrderForListing(listingId);
        int nextOrder = currentMax == null ? 1 : currentMax + 1;
        Instant uploadedAt = clock.instant();

        ListingPhoto saved = photoRepository.save(ListingPhoto.builder()
                .listingId(listingId)
                .url(presignedStorage.publicUrlFor(intent.getFileKey()))
                .displayOrder(nextOrder)
                .caption(req.caption())
                .uploadedAt(uploadedAt)
                .build());

        intent.setConfirmedAt(uploadedAt);
        intent.setConfirmedPhotoId(saved.getId());
        intentRepository.save(intent);

        log.info("Photo upload confirmed: listing={} caller={} key={} photoId={}",
                listingId, callerId, intent.getFileKey(), saved.getId());
        return saved;
    }

    /**
     * Hourly purge of intents older than 24h. Confirmed rows have done their job;
     * unconfirmed rows past expiry are orphans the caller never returned for.
     * Kept outside the request thread so a slow purge doesn't impact uploads.
     */
    @org.springframework.scheduling.annotation.Scheduled(fixedDelayString = "${haven.photos.upload-intent.cleanup-interval-ms:3600000}")
    @Transactional
    public void cleanupExpiredIntents() {
        Instant threshold = clock.instant().minus(Duration.ofHours(24));
        int deleted = intentRepository.deleteCreatedBefore(threshold);
        if (deleted > 0) {
            log.info("Photo upload intent cleanup: deleted {} row(s) older than {}", deleted, threshold);
        }
    }

    /**
     * Common auth gate for both endpoints — the caller must be the listing owner OR
     * have an ACCEPTED {@code agent_listings} row on the listing. Anonymous never
     * reaches this method (controller has {@code @PreAuthorize}); we still check
     * ownership/agent here so the service is honest about its preconditions.
     */
    private void validateAuth(Long callerId, Long listingId) {
        Long ownerId = listingService.ownerOf(listingId)
                .orElseThrow(() -> new ListingNotFoundException(listingId));
        if (ownerId.equals(callerId)) {
            return;
        }
        boolean assignedAgent = agentListingRepository
                .existsByListingIdAndAgentUserIdAndStatus(listingId, callerId, AgentListingStatus.ACCEPTED);
        if (!assignedAgent) {
            throw new NotPropertyOwnerException();
        }
    }

    /** Object key shape: {@code listings/{listingId}/{uuid}-{slug}.{ext}}. */
    private String buildFileKey(Long listingId, String contentType, String originalFilename) {
        String ext = extensionFor(contentType);
        String slug = slugify(originalFilename);
        String uuid = UUID.randomUUID().toString();
        if (slug.isEmpty()) {
            return "listings/" + listingId + "/" + uuid + ext;
        }
        return "listings/" + listingId + "/" + uuid + "-" + slug + ext;
    }

    private static String extensionFor(String contentType) {
        return switch (contentType) {
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            default -> "";
        };
    }

    /** Conservative slug — lowercase ascii alnum + dash; strips the extension. */
    static String slugify(String raw) {
        if (raw == null) {
            return "";
        }
        String base = raw;
        int slash = Math.max(base.lastIndexOf('/'), base.lastIndexOf('\\'));
        if (slash >= 0) {
            base = base.substring(slash + 1);
        }
        int dot = base.lastIndexOf('.');
        if (dot > 0) {
            base = base.substring(0, dot);
        }
        StringBuilder sb = new StringBuilder(base.length());
        boolean dashPending = false;
        for (char c : base.toLowerCase(Locale.ROOT).toCharArray()) {
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')) {
                if (dashPending && sb.length() > 0) {
                    sb.append('-');
                }
                sb.append(c);
                dashPending = false;
            } else {
                dashPending = true;
            }
        }
        String out = sb.toString();
        if (out.length() > 60) {
            out = out.substring(0, 60);
        }
        return out;
    }
}
