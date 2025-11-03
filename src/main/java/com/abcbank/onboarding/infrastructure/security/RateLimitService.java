package com.abcbank.onboarding.infrastructure.security;

import com.abcbank.onboarding.infrastructure.exception.RateLimitExceededException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Redis-based Rate Limiting Service
 * Implements sliding window rate limiting with multiple strategies
 */
@Slf4j
@Service
public class RateLimitService {

    private static final String RATE_LIMIT_KEY_PREFIX = "rate_limit:";

    private final RedisTemplate<String, Object> redisTemplate;

    public RateLimitService(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * Check and consume rate limit for a resource
     * @param key Unique identifier (IP, userId, email, phone, SSN, etc.)
     * @param resource Resource being accessed (e.g., "create_application", "verify_otp")
     * @param limit Maximum requests allowed in window
     * @param windowMillis Time window in milliseconds
     * @throws RateLimitExceededException if limit exceeded
     */
    public void checkRateLimit(String key, String resource, int limit, long windowMillis) {
        if (key == null || resource == null) {
            log.warn("Rate limit check called with null key or resource");
            return;
        }

        String rateLimitKey = buildKey(resource, key);

        try {
            // Get current count
            Long currentCount = getCurrentCount(rateLimitKey);

            if (currentCount != null && currentCount >= limit) {
                long ttl = getRemainingWindowTime(rateLimitKey);
                log.warn("Rate limit exceeded for key: {} on resource: {} (count: {}/{}, reset in: {}ms)",
                        maskKey(key), resource, currentCount, limit, ttl);

                throw new RateLimitExceededException(
                        String.format("Rate limit exceeded for %s. Limit: %d requests per %d ms. Try again in %d ms.",
                                resource, limit, windowMillis, ttl)
                );
            }

            // Increment counter
            Long newCount = redisTemplate.opsForValue().increment(rateLimitKey);

            // Set expiry on first request in window
            if (newCount != null && newCount == 1) {
                redisTemplate.expire(rateLimitKey, windowMillis, TimeUnit.MILLISECONDS);
            }

            log.debug("Rate limit checked for key: {} on resource: {} - count: {}/{}",
                    maskKey(key), resource, newCount, limit);

        } catch (RateLimitExceededException e) {
            throw e; // Re-throw rate limit exception
        } catch (Exception e) {
            log.error("Error checking rate limit for key: {} on resource: {}", maskKey(key), resource, e);
            // Fail open - don't block requests on Redis errors
        }
    }

    /**
     * Check rate limit for IP-based requests (anonymous)
     */
    public void checkIpRateLimit(String ipAddress, String resource, int limit, long windowMillis) {
        checkRateLimit(ipAddress, resource, limit, windowMillis);
    }

    /**
     * Check rate limit for application-specific operations
     */
    public void checkApplicationRateLimit(UUID applicationId, String resource, int limit, long windowMillis) {
        checkRateLimit(applicationId.toString(), resource, limit, windowMillis);
    }

    /**
     * Check rate limit for email domain (prevent bulk signups from same domain)
     */
    public void checkEmailDomainRateLimit(String email, String resource, int limit, long windowMillis) {
        String domain = extractDomain(email);
        if (domain != null) {
            checkRateLimit(domain, resource, limit, windowMillis);
        }
    }

    /**
     * Check rate limit for phone number
     */
    public void checkPhoneRateLimit(String phone, String resource, int limit, long windowMillis) {
        checkRateLimit(phone, resource, limit, windowMillis);
    }

    /**
     * Check rate limit for SSN (should be 1 lifetime)
     */
    public void checkSsnRateLimit(String ssn, String resource) {
        // SSN gets lifetime limit (1 application per SSN)
        String rateLimitKey = buildKey(resource, ssn);
        Boolean exists = redisTemplate.hasKey(rateLimitKey);

        if (Boolean.TRUE.equals(exists)) {
            log.warn("SSN rate limit exceeded - already used: {}", maskSsn(ssn));
            throw new RateLimitExceededException(
                    "An application with this Social Security Number already exists"
            );
        }

        // Mark SSN as used (no expiry - lifetime ban)
        redisTemplate.opsForValue().set(rateLimitKey, 1);
        log.info("SSN rate limit recorded for: {}", maskSsn(ssn));
    }

    /**
     * Get current count for a rate limit key
     */
    public Long getCurrentCount(String rateLimitKey) {
        Object value = redisTemplate.opsForValue().get(rateLimitKey);
        if (value instanceof Integer) {
            return ((Integer) value).longValue();
        } else if (value instanceof Long) {
            return (Long) value;
        }
        return null;
    }

    /**
     * Get remaining time in rate limit window
     */
    public long getRemainingWindowTime(String rateLimitKey) {
        Long ttl = redisTemplate.getExpire(rateLimitKey, TimeUnit.MILLISECONDS);
        return ttl != null && ttl > 0 ? ttl : 0;
    }

    /**
     * Get remaining requests before rate limit
     */
    public RateLimitInfo getRateLimitInfo(String key, String resource, int limit) {
        String rateLimitKey = buildKey(resource, key);
        Long currentCount = getCurrentCount(rateLimitKey);
        long remaining = limit - (currentCount != null ? currentCount : 0);
        long resetTime = getRemainingWindowTime(rateLimitKey);

        return new RateLimitInfo(
                limit,
                currentCount != null ? currentCount : 0,
                Math.max(0, remaining),
                Instant.now().plusMillis(resetTime).getEpochSecond()
        );
    }

    /**
     * Reset rate limit for a key (admin operation)
     */
    public void resetRateLimit(String key, String resource) {
        String rateLimitKey = buildKey(resource, key);
        redisTemplate.delete(rateLimitKey);
        log.info("Rate limit reset for key: {} on resource: {}", maskKey(key), resource);
    }

    /**
     * Reset all rate limits for a resource (admin operation)
     */
    public void resetAllRateLimits(String resource) {
        String pattern = RATE_LIMIT_KEY_PREFIX + resource + ":*";
        var keys = redisTemplate.keys(pattern);
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
            log.info("Reset {} rate limits for resource: {}", keys.size(), resource);
        }
    }

    /**
     * Build Redis key for rate limiting
     */
    private String buildKey(String resource, String identifier) {
        return RATE_LIMIT_KEY_PREFIX + resource + ":" + identifier;
    }

    /**
     * Extract domain from email
     */
    private String extractDomain(String email) {
        if (email != null && email.contains("@")) {
            return email.substring(email.indexOf("@") + 1).toLowerCase();
        }
        return null;
    }

    /**
     * Mask key for logging (GDPR compliant)
     */
    private String maskKey(String key) {
        if (key == null || key.length() <= 4) {
            return "****";
        }
        return key.substring(0, 2) + "****" + key.substring(key.length() - 2);
    }

    /**
     * Mask SSN for logging
     */
    private String maskSsn(String ssn) {
        if (ssn == null || ssn.length() < 4) {
            return "XXX-XX-****";
        }
        return "XXX-XX-" + ssn.substring(ssn.length() - 4);
    }

    /**
     * Rate limit information for response headers
     */
    public static class RateLimitInfo {
        private final int limit;
        private final long current;
        private final long remaining;
        private final long resetTimestamp;

        public RateLimitInfo(int limit, long current, long remaining, long resetTimestamp) {
            this.limit = limit;
            this.current = current;
            this.remaining = remaining;
            this.resetTimestamp = resetTimestamp;
        }

        public int getLimit() {
            return limit;
        }

        public long getCurrent() {
            return current;
        }

        public long getRemaining() {
            return remaining;
        }

        public long getResetTimestamp() {
            return resetTimestamp;
        }
    }
}
