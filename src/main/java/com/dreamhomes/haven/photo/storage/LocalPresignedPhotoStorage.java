package com.dreamhomes.haven.photo.storage;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Default {@link PhotoPresignedStorage} for dev + test. Synthesises pre-signed URLs
 * that point at the same {@code media.dreamhomes.com} host the legacy
 * {@code LocalPhotoStorage} uses — keeps tests free of R2 credentials while still
 * exercising the full intent + confirm pipeline.
 *
 * <p>HEAD behaviour: in-memory bookkeeping. Tests that want to simulate "object
 * landed at R2" call {@link #recordUpload(String, long, String)} (test-only helper);
 * the confirm path then sees a non-empty HEAD result. Without that call HEAD reports
 * missing — exactly what production would surface when the browser never PUT.</p>
 */
@Component
@ConditionalOnProperty(value = "haven.photos.storage", havingValue = "local", matchIfMissing = true)
@Slf4j
public class LocalPresignedPhotoStorage implements PhotoPresignedStorage {

    private final Map<String, HeadResult> uploaded = new ConcurrentHashMap<>();

    @Override
    public URI presignUpload(String fileKey, String contentType, long maxSizeBytes, Duration ttl) {
        // Synthesise a URL that points at media.dreamhomes.com — same host as
        // LocalPhotoStorage so tests can assert against a stable prefix. The query
        // string mimics the AWS-style signature so downstream code paths can still
        // pattern-match against ".../listings/{id}/{uuid}.{ext}?X-Amz-..." if needed.
        String url = "https://media.dreamhomes.com/" + fileKey
                + "?X-Amz-Algorithm=LOCAL-MOCK"
                + "&X-Amz-Expires=" + ttl.getSeconds();
        log.info("LocalPresignedPhotoStorage: synthesised pre-signed URL for key {}", fileKey);
        return URI.create(url);
    }

    @Override
    public HeadResult headObject(String fileKey) {
        HeadResult r = uploaded.get(fileKey);
        return r == null ? HeadResult.missing() : r;
    }

    @Override
    public String publicUrlFor(String fileKey) {
        return "https://media.dreamhomes.com/" + fileKey;
    }

    /**
     * Test-only helper: simulate a successful R2 upload so subsequent {@link #headObject(String)}
     * reports the object exists with the supplied size + content type. Not part of the
     * production interface — call this from ITs to drive the confirm path through HEAD.
     */
    public void recordUpload(String fileKey, long sizeBytes, String contentType) {
        uploaded.put(fileKey, HeadResult.of(sizeBytes, contentType));
    }

    /** Test-only: simulate the object having vanished. */
    public void clearUpload(String fileKey) {
        uploaded.remove(fileKey);
    }
}
