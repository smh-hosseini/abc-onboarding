package com.abcbank.onboarding;

import com.abcbank.onboarding.infrastructure.security.JwtAuthenticationDetails;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.*;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;

/**
 * Base class for integration tests using Testcontainers.
 * Provides PostgreSQL, Redis, and RabbitMQ containers for testing.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = {OnboardingApplication.class, IntegrationTestConfig.class})
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("onboarding_test")
            .withUsername("test")
            .withPassword("test")
            .withReuse(true);

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379)
            .withReuse(true);

    @Container
    protected static RabbitMQContainer rabbitmq = new RabbitMQContainer("rabbitmq:3-management-alpine")
            .withReuse(true);

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected RedisTemplate<String, Object> redisTemplate;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        // PostgreSQL properties
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);

        // Redis properties
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));

        // RabbitMQ properties
        registry.add("spring.rabbitmq.host", rabbitmq::getHost);
        registry.add("spring.rabbitmq.port", rabbitmq::getAmqpPort);
        registry.add("spring.rabbitmq.username", rabbitmq::getAdminUsername);
        registry.add("spring.rabbitmq.password", rabbitmq::getAdminPassword);

        // Test-specific properties
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.jpa.show-sql", () -> "false");

        // Disable external services for testing
        registry.add("notification.email.enabled", () -> "false");
        registry.add("notification.sms.enabled", () -> "false");

        // Use in-memory storage for tests
        registry.add("storage.type", () -> "local");
    }

    @BeforeEach
    void cleanupRedis() {
        // Clean Redis before each test
        if (redisTemplate != null && redisTemplate.getConnectionFactory() != null) {
            try {
                redisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();
            } catch (Exception e) {
                // Ignore cleanup errors
            }
        }
    }

    /**
     * Helper method to perform async requests that return CompletableFuture.
     * This method waits for the async operation to complete before returning the result.
     *
     * @param request The request to perform
     * @return ResultActions with the completed async response
     * @throws Exception if the request fails
     */
    protected ResultActions performAsync(RequestBuilder request) throws Exception {
        var mvcResult = mockMvc.perform(request).andReturn();

        // Check if the request actually started async processing
        if (mvcResult.getRequest().isAsyncStarted()) {
            return mockMvc.perform(asyncDispatch(mvcResult));
        } else {
            // If not async, return the result as-is wrapped in ResultActions
            return mockMvc.perform(request);
        }
    }

    // ==================== Authentication Utility Methods ====================

    /**
     * Test user roles
     */
    protected enum TestRole {
        APPLICANT("APPLICANT"),
        COMPLIANCE_OFFICER("COMPLIANCE_OFFICER"),
        ADMIN("ADMIN");

        private final String roleName;

        TestRole(String roleName) {
            this.roleName = roleName;
        }

        public String getRoleName() {
            return roleName;
        }
    }

    /**
     * Simple test user class for authentication
     */
    protected static class TestUser implements org.springframework.security.core.userdetails.UserDetails {
        private final String username;
        private final Collection<? extends GrantedAuthority> authorities;
        private final UUID applicationId; // For applicant role

        public TestUser(String username, Collection<? extends GrantedAuthority> authorities) {
            this(username, authorities, null);
        }

        public TestUser(String username, Collection<? extends GrantedAuthority> authorities, UUID applicationId) {
            this.username = username;
            this.authorities = authorities;
            this.applicationId = applicationId;
        }

        @Override
        public Collection<? extends GrantedAuthority> getAuthorities() {
            return authorities;
        }

        @Override
        public String getPassword() {
            return null;
        }

        @Override
        public String getUsername() {
            return username;
        }

        @Override
        public boolean isAccountNonExpired() {
            return true;
        }

        @Override
        public boolean isAccountNonLocked() {
            return true;
        }

        @Override
        public boolean isCredentialsNonExpired() {
            return true;
        }

        @Override
        public boolean isEnabled() {
            return true;
        }

        public UUID getApplicationId() {
            return applicationId;
        }
    }

    /**
     * Get a test user with the specified role
     */
    protected TestUser getTestUser(TestRole role) {
        return getTestUser(role, null);
    }

    /**
     * Get a test user with the specified role and application ID
     */
    protected TestUser getTestUser(TestRole role, UUID applicationId) {
        List<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_" + role.getRoleName()));

        return switch (role) {
            case APPLICANT -> new TestUser("app-" + (applicationId != null ? applicationId : UUID.randomUUID()),
                    authorities, applicationId);
            case COMPLIANCE_OFFICER -> new TestUser("officer@abc.nl", authorities);
            case ADMIN -> new TestUser("admin@abc.nl", authorities);
        };
    }

    /**
     * Set authenticated user in SecurityContext
     */
    protected void setAuthenticatedUser(TestUser user) {
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    /**
     * Set authenticated user with role in SecurityContext
     */
    protected TestUser setAuthenticatedUserWithRole(TestRole role) {
        TestUser user = getTestUser(role);
        setAuthenticatedUser(user);
        return user;
    }

    /**
     * Set authenticated user with role and application ID in SecurityContext
     */
    protected TestUser setAuthenticatedUserWithRole(TestRole role, UUID applicationId) {
        TestUser user = getTestUser(role, applicationId);
        setAuthenticatedUser(user);
        return user;
    }

    /**
     * Perform request as a specific role
     */
    protected ResultActions performAsRole(TestRole role, MockHttpServletRequestBuilder requestBuilder) throws Exception {
        TestUser user = getTestUser(role);
        return mockMvc.perform(requestBuilder.with(user(user)));
    }

    /**
     * Helper to create mock Claims for testing
     */
    private Claims createMockClaims(UUID applicationId) {
        Claims claims = Mockito.mock(Claims.class);
        Mockito.when(claims.get("application_id", String.class)).thenReturn(applicationId.toString());
        Mockito.when(claims.get("type", String.class)).thenReturn("applicant_session");
        return claims;
    }

    /**
     * Perform request as applicant with application ID
     */
    protected ResultActions performAsApplicant(UUID applicationId, MockHttpServletRequestBuilder requestBuilder) throws Exception {
        TestUser user = getTestUser(TestRole.APPLICANT, applicationId);

        // Create mock JWT claims with application_id
        Claims claims = createMockClaims(applicationId);
        JwtAuthenticationDetails jwtDetails = new JwtAuthenticationDetails(claims);

        // Create authentication with JWT details
        UsernamePasswordAuthenticationToken auth =
            new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
        auth.setDetails(jwtDetails);

        // Use the authentication() post processor which properly sets up SecurityContext
        return mockMvc.perform(requestBuilder.with(authentication(auth)));
    }

    /**
     * Perform request as applicant
     */
    protected ResultActions performAsApplicant(MockHttpServletRequestBuilder requestBuilder) throws Exception {
        return performAsRole(TestRole.APPLICANT, requestBuilder);
    }

    /**
     * Perform request as compliance officer
     */
    protected ResultActions performAsComplianceOfficer(MockHttpServletRequestBuilder requestBuilder) throws Exception {
        return performAsRole(TestRole.COMPLIANCE_OFFICER, requestBuilder);
    }

    /**
     * Perform request as admin
     */
    protected ResultActions performAsAdmin(MockHttpServletRequestBuilder requestBuilder) throws Exception {
        return performAsRole(TestRole.ADMIN, requestBuilder);
    }

    /**
     * Perform request without authentication (anonymous)
     */
    protected ResultActions performAsAnonymous(MockHttpServletRequestBuilder requestBuilder) throws Exception {
        return mockMvc.perform(requestBuilder);
    }

    /**
     * Perform async request as a specific role
     */
    protected ResultActions performAsyncAsRole(TestRole role, MockHttpServletRequestBuilder requestBuilder) throws Exception {
        TestUser user = getTestUser(role);
        return performAsync(requestBuilder.with(user(user)));
    }

    /**
     * Perform async request as applicant
     */
    protected ResultActions performAsyncAsApplicant(UUID applicationId, MockHttpServletRequestBuilder requestBuilder) throws Exception {
        TestUser user = getTestUser(TestRole.APPLICANT, applicationId);

        // Create mock JWT claims with application_id
        Claims claims = createMockClaims(applicationId);
        JwtAuthenticationDetails jwtDetails = new JwtAuthenticationDetails(claims);

        // Create authentication with JWT details
        UsernamePasswordAuthenticationToken auth =
            new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
        auth.setDetails(jwtDetails);

        // Use the authentication() post processor which properly sets up SecurityContext
        return performAsync(requestBuilder.with(authentication(auth)));
    }

    /**
     * Perform async request as compliance officer
     */
    protected ResultActions performAsyncAsComplianceOfficer(MockHttpServletRequestBuilder requestBuilder) throws Exception {
        return performAsyncAsRole(TestRole.COMPLIANCE_OFFICER, requestBuilder);
    }

    /**
     * Perform async request as admin
     */
    protected ResultActions performAsyncAsAdmin(MockHttpServletRequestBuilder requestBuilder) throws Exception {
        return performAsyncAsRole(TestRole.ADMIN, requestBuilder);
    }
}
