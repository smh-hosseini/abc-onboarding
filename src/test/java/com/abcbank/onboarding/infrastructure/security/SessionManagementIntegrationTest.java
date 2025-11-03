package com.abcbank.onboarding.infrastructure.security;

import com.abcbank.onboarding.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for session management with real Redis
 */
@DisplayName("Session Management Integration Tests")
class SessionManagementIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private SessionService sessionService;

    @Test
    @DisplayName("Should create and validate session successfully")
    void shouldCreateAndValidateSessionSuccessfully() {
        // Given
        UUID userId = UUID.randomUUID();
        String employeeId = "EMP123";
        String email = "officer@abc.nl";
        String[] roles = {"COMPLIANCE_OFFICER"};
        String ipAddress = "192.168.1.1";
        String userAgent = "Mozilla/5.0";

        // When - Create session
        Session session = sessionService.createSession(userId, employeeId, email, roles, ipAddress, userAgent);

        // Then - Session created
        assertThat(session).isNotNull();
        assertThat(session.getSessionId()).isNotNull();
        assertThat(session.getUserId()).isEqualTo(userId);
        assertThat(session.getEmployeeId()).isEqualTo(employeeId);
        assertThat(session.getEmail()).isEqualTo(email);
        assertThat(session.isActive()).isTrue();

        // When - Validate session
        Session validatedSession = sessionService.validateSession(session.getSessionId(), ipAddress, userAgent);

        // Then - Session valid
        assertThat(validatedSession).isNotNull();
        assertThat(validatedSession.getSessionId()).isEqualTo(session.getSessionId());
    }

    @Test
    @DisplayName("Should reject session with different IP address")
    void shouldRejectSessionWithDifferentIpAddress() {
        // Given
        UUID userId = UUID.randomUUID();
        String originalIp = "192.168.1.1";
        String differentIp = "192.168.1.2";

        Session session = sessionService.createSession(
                userId, "EMP123", "officer@abc.nl",
                new String[]{"COMPLIANCE_OFFICER"}, originalIp, "Mozilla/5.0"
        );

        // When - Validate with different IP
        Session result = sessionService.validateSession(session.getSessionId(), differentIp, "Mozilla/5.0");

        // Then - Session invalid
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("Should reject session with different user agent")
    void shouldRejectSessionWithDifferentUserAgent() {
        // Given
        UUID userId = UUID.randomUUID();
        String originalUserAgent = "Mozilla/5.0 (Windows NT 10.0)";
        String differentUserAgent = "Mozilla/5.0 (Macintosh)";

        Session session = sessionService.createSession(
                userId, "EMP123", "officer@abc.nl",
                new String[]{"COMPLIANCE_OFFICER"}, "192.168.1.1", originalUserAgent
        );

        // When - Validate with different user agent
        Session result = sessionService.validateSession(session.getSessionId(), "192.168.1.1", differentUserAgent);

        // Then - Session invalid
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("Should terminate session successfully")
    void shouldTerminateSessionSuccessfully() {
        // Given
        UUID userId = UUID.randomUUID();
        Session session = sessionService.createSession(
                userId, "EMP123", "officer@abc.nl",
                new String[]{"COMPLIANCE_OFFICER"}, "192.168.1.1", "Mozilla/5.0"
        );

        // When - Terminate session
        sessionService.terminateSession(session.getSessionId(), "User logout");

        // Then - Session no longer valid
        Session result = sessionService.validateSession(session.getSessionId(), "192.168.1.1", "Mozilla/5.0");
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("Should refresh session and extend expiry")
    void shouldRefreshSessionAndExtendExpiry() {
        // Given
        UUID userId = UUID.randomUUID();
        Session session = sessionService.createSession(
                userId, "EMP123", "officer@abc.nl",
                new String[]{"COMPLIANCE_OFFICER"}, "192.168.1.1", "Mozilla/5.0"
        );

        // When - Refresh session
        sessionService.refreshSession(session.getSessionId());

        // Then - Session still valid
        Session refreshedSession = sessionService.validateSession(session.getSessionId(), "192.168.1.1", "Mozilla/5.0");
        assertThat(refreshedSession).isNotNull();
        assertThat(refreshedSession.getLastActivityAt()).isAfter(session.getCreatedAt());
    }

    @Test
    @DisplayName("Should enforce concurrent session limit (1 per user)")
    void shouldEnforceConcurrentSessionLimit() {
        // Given
        UUID userId = UUID.randomUUID();

        // When - Create first session
        Session session1 = sessionService.createSession(
                userId, "EMP123", "officer@abc.nl",
                new String[]{"COMPLIANCE_OFFICER"}, "192.168.1.1", "Mozilla/5.0"
        );

        // When - Create second session for same user
        Session session2 = sessionService.createSession(
                userId, "EMP123", "officer@abc.nl",
                new String[]{"COMPLIANCE_OFFICER"}, "192.168.1.2", "Chrome"
        );

        // Then - First session should be terminated
        Session result1 = sessionService.validateSession(session1.getSessionId(), "192.168.1.1", "Mozilla/5.0");
        assertThat(result1).isNull(); // First session terminated

        // And second session should be active
        Session result2 = sessionService.validateSession(session2.getSessionId(), "192.168.1.2", "Chrome");
        assertThat(result2).isNotNull();
        assertThat(result2.getSessionId()).isEqualTo(session2.getSessionId());
    }

    @Test
    @DisplayName("Should terminate all user sessions")
    void shouldTerminateAllUserSessions() {
        // Given
        UUID userId = UUID.randomUUID();

        Session session = sessionService.createSession(
                userId, "EMP123", "officer@abc.nl",
                new String[]{"COMPLIANCE_OFFICER"}, "192.168.1.1", "Mozilla/5.0"
        );

        // When - Terminate all sessions for user
        sessionService.terminateAllUserSessions(userId, "Admin termination");

        // Then - Session no longer valid
        Session result = sessionService.validateSession(session.getSessionId(), "192.168.1.1", "Mozilla/5.0");
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("Should get active session count")
    void shouldGetActiveSessionCount() {
        // Given
        UUID userId1 = UUID.randomUUID();
        UUID userId2 = UUID.randomUUID();

        sessionService.createSession(
                userId1, "EMP123", "officer1@abc.nl",
                new String[]{"COMPLIANCE_OFFICER"}, "192.168.1.1", "Mozilla/5.0"
        );

        sessionService.createSession(
                userId2, "EMP456", "officer2@abc.nl",
                new String[]{"COMPLIANCE_OFFICER"}, "192.168.1.2", "Mozilla/5.0"
        );

        // When
        int count1 = sessionService.getActiveSessionCount(userId1);
        int count2 = sessionService.getActiveSessionCount(userId2);

        // Then
        assertThat(count1).isEqualTo(1);
        assertThat(count2).isEqualTo(1);
    }

    @Test
    @DisplayName("Should store session data in Redis")
    void shouldStoreSessionDataInRedis() {
        // Given
        UUID userId = UUID.randomUUID();
        String sessionId;

        // When - Create session
        Session session = sessionService.createSession(
                userId, "EMP123", "officer@abc.nl",
                new String[]{"COMPLIANCE_OFFICER"}, "192.168.1.1", "Mozilla/5.0"
        );
        sessionId = session.getSessionId();

        // Then - Session retrievable from Redis
        String key = "session:" + sessionId;
        Session storedSession = (Session) redisTemplate.opsForValue().get(key);

        assertThat(storedSession).isNotNull();
        assertThat(storedSession.getSessionId()).isEqualTo(sessionId);
        assertThat(storedSession.getUserId()).isEqualTo(userId);
        assertThat(storedSession.getEmployeeId()).isEqualTo("EMP123");
    }

    @Test
    @DisplayName("Should handle different role types correctly")
    void shouldHandleDifferentRoleTypesCorrectly() {
        // Given - Create officer session
        UUID officerId = UUID.randomUUID();
        Session officerSession = sessionService.createSession(
                officerId, "EMP123", "officer@abc.nl",
                new String[]{"COMPLIANCE_OFFICER"}, "192.168.1.1", "Mozilla/5.0"
        );

        // Given - Create admin session
        UUID adminId = UUID.randomUUID();
        Session adminSession = sessionService.createSession(
                adminId, "ADMIN001", "admin@abc.nl",
                new String[]{"ADMIN", "COMPLIANCE_OFFICER"}, "192.168.1.2", "Mozilla/5.0"
        );

        // Then - Both sessions valid
        assertThat(officerSession.getRoles()).containsExactly("COMPLIANCE_OFFICER");
        assertThat(adminSession.getRoles()).containsExactly("ADMIN", "COMPLIANCE_OFFICER");

        // Validate both
        Session validOfficer = sessionService.validateSession(officerSession.getSessionId(), "192.168.1.1", "Mozilla/5.0");
        Session validAdmin = sessionService.validateSession(adminSession.getSessionId(), "192.168.1.2", "Mozilla/5.0");

        assertThat(validOfficer).isNotNull();
        assertThat(validAdmin).isNotNull();
    }

    @Test
    @DisplayName("Should reject invalid session ID")
    void shouldRejectInvalidSessionId() {
        // Given
        String invalidSessionId = UUID.randomUUID().toString();

        // When
        Session result = sessionService.validateSession(invalidSessionId, "192.168.1.1", "Mozilla/5.0");

        // Then
        assertThat(result).isNull();
    }
}
