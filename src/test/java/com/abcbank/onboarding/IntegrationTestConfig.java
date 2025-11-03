package com.abcbank.onboarding;

import com.abcbank.onboarding.domain.port.out.StorageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Test configuration for providing mock/stub beans for integration tests.
 */
@TestConfiguration
@Profile("test")
public class IntegrationTestConfig {

    /**
     * In-memory storage service for tests.
     * Stores files in memory instead of actual storage.
     */
    @Bean
    @Primary
    public StorageService inMemoryStorageService() {
        return new InMemoryStorageService();
    }

    /**
     * Simple in-memory implementation of StorageService for testing.
     */
    @Slf4j
    private static class InMemoryStorageService implements StorageService {

        private final Map<String, byte[]> storage = new ConcurrentHashMap<>();

        @Override
        public String store(byte[] content, String filename, String contentType) {
            String fileId = UUID.randomUUID().toString();
            storage.put(fileId, content);
            log.debug("File stored in memory: fileId={}, filename={}, size={}", fileId, filename, content.length);
            return fileId;
        }

        @Override
        public byte[] retrieve(String path) {
            byte[] bytes = storage.get(path);
            if (bytes == null) {
                throw new RuntimeException("File not found: " + path);
            }
            log.debug("File retrieved from memory: path={}, size={}", path, bytes.length);
            return bytes;
        }

        @Override
        public String generatePresignedUploadUrl(String filename, Duration expiration) {
            // Return a dummy URL for tests
            return "http://localhost:9000/test-bucket/upload/" + filename;
        }

        @Override
        public String generatePresignedDownloadUrl(String path, Duration expiration) {
            // Return a dummy URL for tests
            return "http://localhost:9000/test-bucket/download/" + path;
        }

        @Override
        public void delete(String path) {
            storage.remove(path);
            log.debug("File deleted from memory: path={}", path);
        }

        @Override
        public boolean exists(String path) {
            return storage.containsKey(path);
        }
    }
}
