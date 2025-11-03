package com.abcbank.onboarding.infrastructure.config;

import io.minio.MinioClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;

/**
 * Storage Configuration for MinIO (local) and S3 (production)
 */
@Slf4j
@Configuration
public class StorageConfig {

    /**
     * MinIO Client for local development
     */
    @Bean
    @Profile("local")
    public MinioClient minioClient(
            @Value("${storage.minio.endpoint}") String endpoint,
            @Value("${storage.minio.access-key}") String accessKey,
            @Value("${storage.minio.secret-key}") String secretKey
    ) {
        log.info("Configuring MinIO client with endpoint: {}", endpoint);

        MinioClient client = MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();

        log.info("MinIO client configured successfully");
        return client;
    }

    /**
     * S3 Client for production
     */
    @Bean
    @Profile("prod")
    public S3Client s3Client(
            @Value("${storage.s3.region:eu-west-1}") String region,
            @Value("${storage.s3.access-key:}") String accessKey,
            @Value("${storage.s3.secret-key:}") String secretKey,
            @Value("${storage.s3.endpoint:}") String endpoint
    ) {
        log.info("Configuring S3 client for region: {}", region);

        // Use IAM role if credentials are not provided
        if (accessKey.isEmpty() || secretKey.isEmpty()) {
            log.info("Using default AWS credentials provider (IAM role)");

            if (!endpoint.isEmpty()) {
                // Custom endpoint (e.g., for LocalStack or custom S3-compatible service)
                return S3Client.builder()
                        .region(Region.of(region))
                        .endpointOverride(URI.create(endpoint))
                        .build();
            }

            return S3Client.builder()
                    .region(Region.of(region))
                    .build();
        }

        // Use explicit credentials
        log.info("Using explicit AWS credentials");
        AwsBasicCredentials credentials = AwsBasicCredentials.create(accessKey, secretKey);

        if (!endpoint.isEmpty()) {
            // Custom endpoint
            return S3Client.builder()
                    .region(Region.of(region))
                    .credentialsProvider(StaticCredentialsProvider.create(credentials))
                    .endpointOverride(URI.create(endpoint))
                    .build();
        }

        return S3Client.builder()
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(credentials))
                .build();
    }

    /**
     * S3 Presigner for generating presigned URLs in production
     */
    @Bean
    @Profile("prod")
    public S3Presigner s3Presigner(
            @Value("${storage.s3.region:eu-west-1}") String region,
            @Value("${storage.s3.access-key:}") String accessKey,
            @Value("${storage.s3.secret-key:}") String secretKey,
            @Value("${storage.s3.endpoint:}") String endpoint
    ) {
        log.info("Configuring S3 Presigner for region: {}", region);

        // Use IAM role if credentials are not provided
        if (accessKey.isEmpty() || secretKey.isEmpty()) {
            if (!endpoint.isEmpty()) {
                return S3Presigner.builder()
                        .region(Region.of(region))
                        .endpointOverride(URI.create(endpoint))
                        .build();
            }

            return S3Presigner.builder()
                    .region(Region.of(region))
                    .build();
        }

        // Use explicit credentials
        AwsBasicCredentials credentials = AwsBasicCredentials.create(accessKey, secretKey);

        if (!endpoint.isEmpty()) {
            return S3Presigner.builder()
                    .region(Region.of(region))
                    .credentialsProvider(StaticCredentialsProvider.create(credentials))
                    .endpointOverride(URI.create(endpoint))
                    .build();
        }

        return S3Presigner.builder()
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(credentials))
                .build();
    }
}
