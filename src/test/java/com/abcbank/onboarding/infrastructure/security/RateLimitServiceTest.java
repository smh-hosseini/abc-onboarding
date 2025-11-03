package com.abcbank.onboarding.infrastructure.security;

import com.abcbank.onboarding.infrastructure.exception.RateLimitExceededException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Rate Limit Service Tests")
class RateLimitServiceTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    private RateLimitService rateLimitService;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        rateLimitService = new RateLimitService(redisTemplate);
    }

    @Test
    @DisplayName("Should allow request when under rate limit")
    void shouldAllowRequestWhenUnderRateLimit() {
        // Given
        String key = "192.168.1.1";
        String resource = "create_application";
        int limit = 5;
        long windowMillis = 3600000L;

        when(valueOperations.get(anyString())).thenReturn(2); // Current count is 2
        when(valueOperations.increment(anyString())).thenReturn(3L); // Increment to 3

        // When/Then - Should not throw exception
        assertThatCode(() -> rateLimitService.checkRateLimit(key, resource, limit, windowMillis))
                .doesNotThrowAnyException();

        verify(valueOperations).get(anyString());
        verify(valueOperations).increment(anyString());
    }

    @Test
    @DisplayName("Should block request when rate limit exceeded")
    void shouldBlockRequestWhenRateLimitExceeded() {
        // Given
        String key = "192.168.1.1";
        String resource = "create_application";
        int limit = 5;
        long windowMillis = 3600000L;

        when(valueOperations.get(anyString())).thenReturn(5); // Already at limit
        when(redisTemplate.getExpire(anyString(), eq(TimeUnit.MILLISECONDS))).thenReturn(1800000L); // 30 min remaining

        // When/Then
        assertThatThrownBy(() -> rateLimitService.checkRateLimit(key, resource, limit, windowMillis))
                .isInstanceOf(RateLimitExceededException.class)
                .hasMessageContaining("Rate limit exceeded")
                .hasMessageContaining(resource)
                .hasMessageContaining(String.valueOf(limit));
    }

    @Test
    @DisplayName("Should set expiry on first request")
    void shouldSetExpiryOnFirstRequest() {
        // Given
        String key = "192.168.1.1";
        String resource = "create_application";
        int limit = 5;
        long windowMillis = 3600000L;

        when(valueOperations.get(anyString())).thenReturn(null); // No previous requests
        when(valueOperations.increment(anyString())).thenReturn(1L); // First request

        // When
        rateLimitService.checkRateLimit(key, resource, limit, windowMillis);

        // Then
        verify(redisTemplate).expire(anyString(), eq(windowMillis), eq(TimeUnit.MILLISECONDS));
    }

    @Test
    @DisplayName("Should not set expiry on subsequent requests")
    void shouldNotSetExpiryOnSubsequentRequests() {
        // Given
        String key = "192.168.1.1";
        String resource = "create_application";
        int limit = 5;
        long windowMillis = 3600000L;

        when(valueOperations.get(anyString())).thenReturn(2); // Already has requests
        when(valueOperations.increment(anyString())).thenReturn(3L); // Increment

        // When
        rateLimitService.checkRateLimit(key, resource, limit, windowMillis);

        // Then
        verify(redisTemplate, never()).expire(anyString(), anyLong(), any(TimeUnit.class));
    }

    @Test
    @DisplayName("Should handle null key gracefully")
    void shouldHandleNullKeyGracefully() {
        // Given
        String resource = "create_application";
        int limit = 5;
        long windowMillis = 3600000L;

        // When/Then - Should not throw exception
        assertThatCode(() -> rateLimitService.checkRateLimit(null, resource, limit, windowMillis))
                .doesNotThrowAnyException();

        verify(valueOperations, never()).get(anyString());
        verify(valueOperations, never()).increment(anyString());
    }

    @Test
    @DisplayName("Should handle null resource gracefully")
    void shouldHandleNullResourceGracefully() {
        // Given
        String key = "192.168.1.1";
        int limit = 5;
        long windowMillis = 3600000L;

        // When/Then - Should not throw exception
        assertThatCode(() -> rateLimitService.checkRateLimit(key, null, limit, windowMillis))
                .doesNotThrowAnyException();

        verify(valueOperations, never()).get(anyString());
        verify(valueOperations, never()).increment(anyString());
    }

    @Test
    @DisplayName("Should check application rate limit")
    void shouldCheckApplicationRateLimit() {
        // Given
        UUID applicationId = UUID.randomUUID();
        String resource = "verify_otp";
        int limit = 3;
        long windowMillis = 3600000L;

        when(valueOperations.get(anyString())).thenReturn(1);
        when(valueOperations.increment(anyString())).thenReturn(2L);

        // When
        rateLimitService.checkApplicationRateLimit(applicationId, resource, limit, windowMillis);

        // Then
        verify(valueOperations).get(contains(applicationId.toString()));
        verify(valueOperations).increment(contains(applicationId.toString()));
    }

    @Test
    @DisplayName("Should check email domain rate limit")
    void shouldCheckEmailDomainRateLimit() {
        // Given
        String email = "user@example.com";
        String resource = "create_application";
        int limit = 10;
        long windowMillis = 3600000L;

        when(valueOperations.get(anyString())).thenReturn(5);
        when(valueOperations.increment(anyString())).thenReturn(6L);

        // When
        rateLimitService.checkEmailDomainRateLimit(email, resource, limit, windowMillis);

        // Then
        verify(valueOperations).get(contains("example.com"));
        verify(valueOperations).increment(contains("example.com"));
    }

    @Test
    @DisplayName("Should check phone rate limit")
    void shouldCheckPhoneRateLimit() {
        // Given
        String phone = "+31612345678";
        String resource = "send_otp";
        int limit = 3;
        long windowMillis = 3600000L;

        when(valueOperations.get(anyString())).thenReturn(1);
        when(valueOperations.increment(anyString())).thenReturn(2L);

        // When
        rateLimitService.checkPhoneRateLimit(phone, resource, limit, windowMillis);

        // Then
        verify(valueOperations).get(contains(phone));
        verify(valueOperations).increment(contains(phone));
    }

    @Test
    @DisplayName("Should block SSN if already used")
    void shouldBlockSsnIfAlreadyUsed() {
        // Given
        String ssn = "123456789";
        String resource = "ssn_check";

        when(redisTemplate.hasKey(anyString())).thenReturn(true);

        // When/Then
        assertThatThrownBy(() -> rateLimitService.checkSsnRateLimit(ssn, resource))
                .isInstanceOf(RateLimitExceededException.class)
                .hasMessageContaining("Social Security Number already exists");
    }

    @Test
    @DisplayName("Should allow SSN if not used before")
    void shouldAllowSsnIfNotUsedBefore() {
        // Given
        String ssn = "123456789";
        String resource = "ssn_check";

        when(redisTemplate.hasKey(anyString())).thenReturn(false);

        // When
        rateLimitService.checkSsnRateLimit(ssn, resource);

        // Then
        verify(valueOperations).set(anyString(), eq(1));
    }

    @Test
    @DisplayName("Should get rate limit info correctly")
    void shouldGetRateLimitInfoCorrectly() {
        // Given
        String key = "192.168.1.1";
        String resource = "create_application";
        int limit = 5;

        when(valueOperations.get(anyString())).thenReturn(3);
        when(redisTemplate.getExpire(anyString(), eq(TimeUnit.MILLISECONDS))).thenReturn(1800000L);

        // When
        RateLimitService.RateLimitInfo info = rateLimitService.getRateLimitInfo(key, resource, limit);

        // Then
        assertThat(info).isNotNull();
        assertThat(info.getLimit()).isEqualTo(5);
        assertThat(info.getCurrent()).isEqualTo(3);
        assertThat(info.getRemaining()).isEqualTo(2);
        assertThat(info.getResetTimestamp()).isPositive();
    }

    @Test
    @DisplayName("Should return zero remaining when at limit")
    void shouldReturnZeroRemainingWhenAtLimit() {
        // Given
        String key = "192.168.1.1";
        String resource = "create_application";
        int limit = 5;

        when(valueOperations.get(anyString())).thenReturn(5);
        when(redisTemplate.getExpire(anyString(), eq(TimeUnit.MILLISECONDS))).thenReturn(1800000L);

        // When
        RateLimitService.RateLimitInfo info = rateLimitService.getRateLimitInfo(key, resource, limit);

        // Then
        assertThat(info.getRemaining()).isZero();
    }

    @Test
    @DisplayName("Should return full limit when no requests made")
    void shouldReturnFullLimitWhenNoRequestsMade() {
        // Given
        String key = "192.168.1.1";
        String resource = "create_application";
        int limit = 5;

        when(valueOperations.get(anyString())).thenReturn(null);
        when(redisTemplate.getExpire(anyString(), eq(TimeUnit.MILLISECONDS))).thenReturn(0L);

        // When
        RateLimitService.RateLimitInfo info = rateLimitService.getRateLimitInfo(key, resource, limit);

        // Then
        assertThat(info.getCurrent()).isZero();
        assertThat(info.getRemaining()).isEqualTo(5);
    }

    @Test
    @DisplayName("Should reset rate limit for key")
    void shouldResetRateLimitForKey() {
        // Given
        String key = "192.168.1.1";
        String resource = "create_application";

        // When
        rateLimitService.resetRateLimit(key, resource);

        // Then
        verify(redisTemplate).delete(contains(resource));
        verify(redisTemplate).delete(contains(key));
    }

    @Test
    @DisplayName("Should reset all rate limits for resource")
    void shouldResetAllRateLimitsForResource() {
        // Given
        String resource = "create_application";
        Set<String> keys = new HashSet<>();
        keys.add("rate_limit:create_application:192.168.1.1");
        keys.add("rate_limit:create_application:192.168.1.2");

        when(redisTemplate.keys(anyString())).thenReturn(keys);

        // When
        rateLimitService.resetAllRateLimits(resource);

        // Then
        verify(redisTemplate).keys(contains(resource));
        verify(redisTemplate).delete(eq(keys));
    }

    @Test
    @DisplayName("Should handle empty keys when resetting all")
    void shouldHandleEmptyKeysWhenResettingAll() {
        // Given
        String resource = "create_application";
        when(redisTemplate.keys(anyString())).thenReturn(new HashSet<>());

        // When
        rateLimitService.resetAllRateLimits(resource);

        // Then
        verify(redisTemplate).keys(anyString());
        verify(redisTemplate, never()).delete(any(Set.class));
    }

    @Test
    @DisplayName("Should fail open on Redis error")
    void shouldFailOpenOnRedisError() {
        // Given
        String key = "192.168.1.1";
        String resource = "create_application";
        int limit = 5;
        long windowMillis = 3600000L;

        when(valueOperations.get(anyString())).thenThrow(new RuntimeException("Redis connection error"));

        // When/Then - Should not throw exception (fail open)
        assertThatCode(() -> rateLimitService.checkRateLimit(key, resource, limit, windowMillis))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Should handle Integer and Long count values")
    void shouldHandleIntegerAndLongCountValues() {
        // Given
        String key = "192.168.1.1";
        String resource = "create_application";

        // Test with Integer
        when(valueOperations.get(anyString())).thenReturn(3);
        Long count1 = rateLimitService.getCurrentCount("rate_limit:test:key");
        assertThat(count1).isEqualTo(3L);

        // Test with Long
        when(valueOperations.get(anyString())).thenReturn(3L);
        Long count2 = rateLimitService.getCurrentCount("rate_limit:test:key");
        assertThat(count2).isEqualTo(3L);

        // Test with null
        when(valueOperations.get(anyString())).thenReturn(null);
        Long count3 = rateLimitService.getCurrentCount("rate_limit:test:key");
        assertThat(count3).isNull();
    }

    @Test
    @DisplayName("Should get remaining window time correctly")
    void shouldGetRemainingWindowTimeCorrectly() {
        // Given
        String rateLimitKey = "rate_limit:test:key";
        when(redisTemplate.getExpire(eq(rateLimitKey), eq(TimeUnit.MILLISECONDS))).thenReturn(1800000L);

        // When
        long remaining = rateLimitService.getRemainingWindowTime(rateLimitKey);

        // Then
        assertThat(remaining).isEqualTo(1800000L);
    }

    @Test
    @DisplayName("Should return zero for expired key")
    void shouldReturnZeroForExpiredKey() {
        // Given
        String rateLimitKey = "rate_limit:test:key";
        when(redisTemplate.getExpire(eq(rateLimitKey), eq(TimeUnit.MILLISECONDS))).thenReturn(-1L);

        // When
        long remaining = rateLimitService.getRemainingWindowTime(rateLimitKey);

        // Then
        assertThat(remaining).isZero();
    }
}
