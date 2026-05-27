package com.dreamhomes.haven.photo.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;

/**
 * Wires the {@link S3Client} pointed at Cloudflare R2 — only when
 * {@code haven.photos.storage=r2} is set. Local dev + tests run with the default
 * {@code local} storage and never instantiate an S3 client at all.
 *
 * <p>R2-specific quirks vs. AWS S3:
 * <ul>
 *   <li>Custom endpoint URI ({@code https://<account-id>.r2.cloudflarestorage.com}).</li>
 *   <li>Region is functionally meaningless but the SDK still requires one — set to
 *       {@code auto} per Cloudflare's recommendation.</li>
 *   <li>Path-style addressing on uploads (R2 doesn't support virtual-hosted style
 *       for write paths reliably).</li>
 * </ul>
 */
@Configuration
@ConditionalOnProperty(value = "haven.photos.storage", havingValue = "r2")
public class R2ClientConfig {

    @Bean
    S3Client r2S3Client(
            @Value("${haven.photos.r2.endpoint}") String endpoint,
            @Value("${haven.photos.r2.access-key-id}") String accessKeyId,
            @Value("${haven.photos.r2.secret-access-key}") String secretAccessKey) {
        return S3Client.builder()
                .endpointOverride(URI.create(endpoint))
                .region(Region.of("auto"))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKeyId, secretAccessKey)))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(true)
                        .build())
                .build();
    }

    /**
     * Item 2 — companion {@link S3Presigner} bound to the same R2 endpoint + credentials.
     * The presigner is a separate object from the {@link S3Client} (different request
     * lifecycle); both must point at the R2 endpoint for the signed URL to verify against
     * the right host.
     */
    @Bean
    S3Presigner r2S3Presigner(
            @Value("${haven.photos.r2.endpoint}") String endpoint,
            @Value("${haven.photos.r2.access-key-id}") String accessKeyId,
            @Value("${haven.photos.r2.secret-access-key}") String secretAccessKey) {
        return S3Presigner.builder()
                .endpointOverride(URI.create(endpoint))
                .region(Region.of("auto"))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKeyId, secretAccessKey)))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(true)
                        .build())
                .build();
    }
}
