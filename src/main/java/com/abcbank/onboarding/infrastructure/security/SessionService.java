package com.abcbank.onboarding.infrastructure.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Session Management Service
 * Manages Redis-based sessions for COMPLIANCE_OFFICER and ADMIN users
 */
@Slf4j
@Service
public class SessionService {

    private static final String SESSION_KEY_PREFIX = "session:";
    private static final String USER_SESSIONS_KEY_PREFIX = "user_sessions:";

    private final RedisTemplate<String, Object> redisTemplate;
    private final long idleTimeout;
    private final long officerAbsoluteTimeout;
    private final long adminAbsoluteTimeout;
    private final int maxConcurrentSessions;

    public SessionService(
            RedisTemplate<String, Object> redisTemplate,
            @Value("${session.timeout.idle:900000}") long idleTimeout,
            @Value("${session.timeout.officer-absolute:28800000}") long officerAbsoluteTimeout,
            @Value("${session.timeout.admin-absolute:14400000}") long adminAbsoluteTimeout,
            @Value("${session.max-concurrent:1}") int maxConcurrentSessions
    ) {
        this.redisTemplate = redisTemplate;
        this.idleTimeout = idleTimeout;
        this.officerAbsoluteTimeout = officerAbsoluteTimeout;
        this.adminAbsoluteTimeout = adminAbsoluteTimeout;
        this.maxConcurrentSessions = maxConcurrentSessions;

        log.info("SessionService initialized - Idle: {}ms, Officer: {}ms, Admin: {}ms, MaxSessions: {}",
                idleTimeout, officerAbsoluteTimeout, adminAbsoluteTimeout, maxConcurrentSessions);
    }

    /**
     * Create a new session for a user
     * Terminates existing sessions if max concurrent limit is reached
     */
    public Session createSession(UUID userId, String employeeId, String email,
                                  String[] roles, String ipAddress, String userAgent) {
        log.info("Creating session for user: {}", employeeId);

        // Generate unique session ID
        String sessionId = "sess_" + UUID.randomUUID().toString();

        // Determine timeout based on role
        long absoluteTimeout = isAdmin(roles) ? adminAbsoluteTimeout : officerAbsoluteTimeout;
        LocalDateTime expiresAt = LocalDateTime.now().plusNanos(absoluteTimeout * 1_000_000);

        // Create session object
        Session session = new Session(sessionId, userId, employeeId, email,
                roles, ipAddress, userAgent, expiresAt);

        // Terminate existing sessions if limit exceeded
        terminateExistingSessions(userId, employeeId);

        // Store session in Redis
        String sessionKey = SESSION_KEY_PREFIX + sessionId;
        redisTemplate.opsForValue().set(sessionKey, session, absoluteTimeout, TimeUnit.MILLISECONDS);

        // Track user sessions
        String userSessionsKey = USER_SESSIONS_KEY_PREFIX + userId.toString();
        redisTemplate.opsForSet().add(userSessionsKey, sessionId);
        redisTemplate.expire(userSessionsKey, absoluteTimeout, TimeUnit.MILLISECONDS);

        log.info("Session created successfully: {} for user: {} with timeout: {}ms",
                sessionId, employeeId, absoluteTimeout);

        return session;
    }

    /**
     * Validate session and check for anomalies
     */
    public Session validateSession(String sessionId, String currentIp, String currentUserAgent) {
        if (sessionId == null || sessionId.trim().isEmpty()) {
            log.debug("Session ID is null or empty");
            return null;
        }

        String sessionKey = SESSION_KEY_PREFIX + sessionId;
        Session session = (Session) redisTemplate.opsForValue().get(sessionKey);

        if (session == null) {
            log.warn("Session not found: {}", sessionId);
            return null;
        }

        if (!session.isActive()) {
            log.warn("Session is inactive: {}", sessionId);
            return null;
        }

        if (session.isExpired()) {
            log.warn("Session expired: {}", sessionId);
            terminateSession(sessionId, "Session expired");
            return null;
        }

        if (session.isIdleTimeout(idleTimeout)) {
            log.warn("Session idle timeout: {}", sessionId);
            terminateSession(sessionId, "Idle timeout");
            return null;
        }

        // Anomaly detection: IP address change
        if (!session.getIpAddress().equals(currentIp)) {
            log.error("Security alert: IP address change detected for session: {} (old: {}, new: {})",
                    sessionId, session.getIpAddress(), currentIp);
            terminateSession(sessionId, "IP address change detected");
            return null;
        }

        // Anomaly detection: User-Agent change
        if (!session.getUserAgent().equals(currentUserAgent)) {
            log.error("Security alert: User-Agent change detected for session: {}", sessionId);
            terminateSession(sessionId, "User-Agent change detected");
            return null;
        }

        // Update last activity time
        session.updateActivity();
        redisTemplate.opsForValue().set(sessionKey, session);

        log.debug("Session validated successfully: {}", sessionId);
        return session;
    }

    /**
     * Terminate a specific session
     */
    public void terminateSession(String sessionId, String reason) {
        log.info("Terminating session: {} - Reason: {}", sessionId, reason);

        String sessionKey = SESSION_KEY_PREFIX + sessionId;
        Session session = (Session) redisTemplate.opsForValue().get(sessionKey);

        if (session != null) {
            // Remove from user sessions set
            String userSessionsKey = USER_SESSIONS_KEY_PREFIX + session.getUserId().toString();
            redisTemplate.opsForSet().remove(userSessionsKey, sessionId);

            // Delete session
            redisTemplate.delete(sessionKey);

            log.info("Session terminated: {} for user: {}", sessionId, session.getEmployeeId());
        }
    }

    /**
     * Terminate all sessions for a user
     */
    public void terminateAllUserSessions(UUID userId, String reason) {
        log.info("Terminating all sessions for user: {} - Reason: {}", userId, reason);

        String userSessionsKey = USER_SESSIONS_KEY_PREFIX + userId.toString();
        Set<Object> sessionIds = redisTemplate.opsForSet().members(userSessionsKey);

        if (sessionIds != null) {
            for (Object sessionId : sessionIds) {
                terminateSession((String) sessionId, reason);
            }
        }

        redisTemplate.delete(userSessionsKey);
        log.info("All sessions terminated for user: {}", userId);
    }

    /**
     * Terminate existing sessions if concurrent limit exceeded
     */
    private void terminateExistingSessions(UUID userId, String employeeId) {
        String userSessionsKey = USER_SESSIONS_KEY_PREFIX + userId.toString();
        Long sessionCount = redisTemplate.opsForSet().size(userSessionsKey);

        if (sessionCount != null && sessionCount >= maxConcurrentSessions) {
            log.warn("Max concurrent sessions reached for user: {}. Terminating existing sessions.", employeeId);
            terminateAllUserSessions(userId, "New login - max concurrent sessions exceeded");
        }
    }

    /**
     * Get active session count for user
     */
    public int getActiveSessionCount(UUID userId) {
        String userSessionsKey = USER_SESSIONS_KEY_PREFIX + userId.toString();
        Long count = redisTemplate.opsForSet().size(userSessionsKey);
        return count != null ? count.intValue() : 0;
    }

    /**
     * Refresh session expiry
     */
    public void refreshSession(String sessionId) {
        String sessionKey = SESSION_KEY_PREFIX + sessionId;
        Session session = (Session) redisTemplate.opsForValue().get(sessionKey);

        if (session != null) {
            long absoluteTimeout = isAdmin(session.getRoles()) ? adminAbsoluteTimeout : officerAbsoluteTimeout;

            // Check if session hasn't exceeded absolute timeout
            Duration duration = Duration.between(session.getCreatedAt(), LocalDateTime.now());
            if (duration.toMillis() < absoluteTimeout) {
                session.updateActivity();
                long remainingTime = absoluteTimeout - duration.toMillis();
                redisTemplate.opsForValue().set(sessionKey, session, remainingTime, TimeUnit.MILLISECONDS);
                log.debug("Session refreshed: {}", sessionId);
            } else {
                log.warn("Cannot refresh session: absolute timeout exceeded for session: {}", sessionId);
                terminateSession(sessionId, "Absolute timeout exceeded");
            }
        }
    }

    /**
     * Check if user has ADMIN role
     */
    private boolean isAdmin(String[] roles) {
        if (roles == null) {
            return false;
        }
        for (String role : roles) {
            if ("ADMIN".equals(role)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Clean up expired sessions (can be called by scheduler)
     */
    public void cleanupExpiredSessions() {
        log.info("Starting cleanup of expired sessions");
        // Redis TTL handles automatic deletion, but this can be used for additional cleanup logic
        // or reporting purposes
    }
}
