package com.dreamhomes.haven.photo.storage;

import com.dreamhomes.haven.photo.exception.PhotoUploadException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.util.UUID;

/**
 * Production {@link PhotoStorage} backed by Cloudflare R2 via the AWS SDK v2 S3
 * client. Activated by {@code haven.photos.storage=r2}.
 *
 * <p>R2 is wire-compatible with S3, so the only difference vs. AWS S3 is the
 * endpoint (configured at the {@link S3Client} bean). Bucket name + public-URL
 * base are wired in via properties so the same config can target dev / staging /
 * prod buckets without code change.</p>
 *
 * <p>Object keys are shaped as {@code listings/{listingId}/{uuid}.{ext}} so:
 * <ul>
 *   <li>Listing-scoped lifecycle policies (e.g. delete all photos when a listing is
 *       removed) can be applied with a key prefix.</li>
 *   <li>Concurrent uploads of the same filename don't collide (UUID).</li>
 *   <li>Original extension is preserved so content-type sniffing on the CDN works.</li>
 * </ul>
 */
@Component
@ConditionalOnProperty(value = "haven.photos.storage", havingValue = "r2")
@Slf4j
public class R2PhotoStorage implements PhotoStorage {

    private final S3Client s3;
    private final String bucket;
    private final String publicBaseUrl;

    public R2PhotoStorage(
            S3Client s3,
            @Value("${haven.photos.r2.bucket}") String bucket,
            @Value("${haven.photos.r2.public-base-url}") String publicBaseUrl) {
        this.s3 = s3;
        this.bucket = bucket;
        // Strip any trailing slash so the join below is unambiguous.
        this.publicBaseUrl = publicBaseUrl.endsWith("/")
                ? publicBaseUrl.substring(0, publicBaseUrl.length() - 1)
                : publicBaseUrl;
    }

    @Override
    public String upload(MultipartFile file, Long listingId) {
        if (file == null || file.isEmpty()) {
            throw new PhotoUploadException("uploaded file is empty");
        }
        String key = "listings/" + listingId + "/" + UUID.randomUUID() + extensionOf(file);
        String contentType = file.getContentType() != null
                ? file.getContentType()
                : "application/octet-stream";
        try {
            s3.putObject(
                    PutObjectRequest.builder()
                            .bucket(bucket)
                            .key(key)
                            .contentType(contentType)
                            .contentLength(file.getSize())
                            .build(),
                    RequestBody.fromInputStream(file.getInputStream(), file.getSize()));
        } catch (IOException e) {
            throw new PhotoUploadException("could not read uploaded file", e);
        } catch (RuntimeException e) {
            // S3Exception, SdkException — anything from the SDK surfaces as a 4xx to
            // the caller. Most of these are misconfig (wrong bucket, expired creds)
            // which is technically server-side, but the caller still needs to retry
            // the upload after the operator fixes things.
            throw new PhotoUploadException("R2 upload failed: " + e.getMessage(), e);
        }
        String url = publicBaseUrl + "/" + key;
        log.info("R2PhotoStorage: uploaded listing {} photo to {}", listingId, url);
        return url;
    }

    private static String extensionOf(MultipartFile file) {
        String original = file.getOriginalFilename();
        if (original == null) return "";
        int dot = original.lastIndexOf('.');
        return dot < 0 ? "" : original.substring(dot).toLowerCase();
    }
}
