package com.dreamhomes.haven.photo.storage;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.net.URI;
import java.time.Duration;

/**
 * Production {@link PhotoPresignedStorage} for Cloudflare R2. Active when
 * {@code haven.photos.storage=r2}.
 *
 * <p>Pre-signed PUT URLs are minted by {@link S3Presigner} (a separate bean from the
 * upload-time {@link S3Client} — R2 needs the {@link S3Presigner} pointed at the same
 * endpoint and credentials). HEAD uses the regular S3 client; R2 supports it the same
 * way S3 does.</p>
 */
@Component
@ConditionalOnProperty(value = "haven.photos.storage", havingValue = "r2")
@Slf4j
public class R2PresignedPhotoStorage implements PhotoPresignedStorage {

    private final S3Presigner presigner;
    private final S3Client s3;
    private final String bucket;
    private final String publicBaseUrl;

    public R2PresignedPhotoStorage(
            S3Presigner presigner,
            S3Client s3,
            @Value("${haven.photos.r2.bucket}") String bucket,
            @Value("${haven.photos.r2.public-base-url}") String publicBaseUrl) {
        this.presigner = presigner;
        this.s3 = s3;
        this.bucket = bucket;
        this.publicBaseUrl = publicBaseUrl.endsWith("/")
                ? publicBaseUrl.substring(0, publicBaseUrl.length() - 1)
                : publicBaseUrl;
    }

    @Override
    public URI presignUpload(String fileKey, String contentType, long maxSizeBytes, Duration ttl) {
        PutObjectRequest put = PutObjectRequest.builder()
                .bucket(bucket)
                .key(fileKey)
                .contentType(contentType)
                .contentLength(maxSizeBytes)
                .build();
        PresignedPutObjectRequest presigned = presigner.presignPutObject(
                PutObjectPresignRequest.builder()
                        .signatureDuration(ttl)
                        .putObjectRequest(put)
                        .build());
        URI url = presigned.url() == null ? null : URI.create(presigned.url().toString());
        log.info("R2PresignedPhotoStorage: minted pre-signed PUT for key {} (ttl={}s)",
                fileKey, ttl.getSeconds());
        return url;
    }

    @Override
    public HeadResult headObject(String fileKey) {
        try {
            HeadObjectResponse resp = s3.headObject(HeadObjectRequest.builder()
                    .bucket(bucket)
                    .key(fileKey)
                    .build());
            return HeadResult.of(resp.contentLength() == null ? 0L : resp.contentLength(),
                    resp.contentType());
        } catch (NoSuchKeyException e) {
            return HeadResult.missing();
        } catch (RuntimeException e) {
            // S3Exception sub-classes: 404 surfaces here too on some SDK versions
            String msg = e.getMessage() == null ? "" : e.getMessage().toLowerCase();
            if (msg.contains("not found") || msg.contains("404") || msg.contains("nosuchkey")) {
                return HeadResult.missing();
            }
            throw e;
        }
    }

    @Override
    public String publicUrlFor(String fileKey) {
        return publicBaseUrl + "/" + fileKey;
    }
}
