package com.dreamhomes.haven.verification.storage;

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
 * Production {@link VerificationDocumentStorage} backed by Cloudflare R2 via the AWS
 * SDK v2 S3 client (the same bean used by {@code R2PhotoStorage}).
 *
 * <p>Object keys are shaped as {@code verifications/{userId}/{uuid}.{ext}} so a
 * future user-deletion lifecycle policy can scope by user prefix without touching
 * the listing-photo namespace.</p>
 *
 * <p>Bucket is the same as photos for now — separated only by key prefix. If the
 * trust team later requires a stricter ACL on documents, swap the {@code bucket}
 * binding for a dedicated {@code haven.verifications.r2.bucket} property.</p>
 */
@Component
@ConditionalOnProperty(value = "haven.photos.storage", havingValue = "r2")
@Slf4j
public class R2VerificationDocumentStorage implements VerificationDocumentStorage {

    private final S3Client s3;
    private final String bucket;
    private final String publicBaseUrl;

    public R2VerificationDocumentStorage(
            S3Client s3,
            @Value("${haven.photos.r2.bucket}") String bucket,
            @Value("${haven.photos.r2.public-base-url}") String publicBaseUrl) {
        this.s3 = s3;
        this.bucket = bucket;
        this.publicBaseUrl = publicBaseUrl.endsWith("/")
                ? publicBaseUrl.substring(0, publicBaseUrl.length() - 1)
                : publicBaseUrl;
    }

    @Override
    public String upload(MultipartFile file, Long userId) {
        if (file == null || file.isEmpty()) {
            throw new PhotoUploadException("uploaded file is empty");
        }
        String key = "verifications/" + userId + "/" + UUID.randomUUID() + extensionOf(file);
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
            throw new PhotoUploadException("R2 upload failed: " + e.getMessage(), e);
        }
        String url = publicBaseUrl + "/" + key;
        log.info("R2VerificationDocumentStorage: uploaded user {} doc to {}", userId, url);
        return url;
    }

    private static String extensionOf(MultipartFile file) {
        String original = file.getOriginalFilename();
        if (original == null) return "";
        int dot = original.lastIndexOf('.');
        return dot < 0 ? "" : original.substring(dot).toLowerCase();
    }
}
