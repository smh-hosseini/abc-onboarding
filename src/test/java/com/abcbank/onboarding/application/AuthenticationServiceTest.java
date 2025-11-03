package com.abcbank.onboarding.application;

import com.abcbank.onboarding.domain.model.User;
import com.abcbank.onboarding.domain.model.UserRole;
import com.abcbank.onboarding.domain.port.out.RefreshTokenRepository;
import com.abcbank.onboarding.domain.port.out.UserRepository;
import com.abcbank.onboarding.infrastructure.security.JwtTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for AuthenticationService.
 * Tests all authentication use cases including corner cases.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AuthenticationService Unit Tests")
class AuthenticationServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private JwtTokenService jwtTokenService;

    @Mock
    private PasswordEncoder passwordEncoder;

    private AuthenticationService authenticationService;

    private User testUser;
    private UUID testUserId;

    @BeforeEach
    void setUp() {
        authenticationService = new AuthenticationService(
                userRepository,
                refreshTokenRepository,
                jwtTokenService,
                passwordEncoder
        );

        testUserId = UUID.randomUUID();
        testUser = new User(
                testUserId,
                "officer@abc.nl",
                "$2a$12$hashedPassword",
                "officer@abc.nl",
                "Test Officer",
                UserRole.COMPLIANCE_OFFICER
        );
    }

    // ==================== authenticate() Tests ====================

    @Test
    @DisplayName("Should successfully authenticate user with valid credentials")
    void shouldAuthenticateWithValidCredentials() {
        // Given
        String username = "officer@abc.nl";
        String password = "Officer123!";
        String accessToken = "access-token-123";
        String refreshToken = "refresh-token-456";
        String refreshTokenHash = "refresh-token-hash";

        when(userRepository.findByUsername(username)).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches(password, testUser.getPasswordHash())).thenReturn(true);
        when(jwtTokenService.generateAccessToken(any(User.class), anyString())).thenReturn(accessToken);
        when(jwtTokenService.generateRefreshToken()).thenReturn(
                new JwtTokenService.RefreshTokenPair(refreshToken, refreshTokenHash)
        );
        when(jwtTokenService.getRefreshTokenExpiry()).thenReturn(LocalDateTime.now().plusDays(30));
        when(jwtTokenService.getRefreshTokenExpiryMillis()).thenReturn(2592000000L);
        when(userRepository.save(any(User.class))).thenReturn(testUser);

        // When
        AuthenticationService.AuthenticationResult result = authenticationService.authenticate(username, password);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.accessToken()).isEqualTo(accessToken);
        assertThat(result.refreshToken()).isEqualTo(refreshToken);
        assertThat(result.userId()).isEqualTo(testUserId);
        assertThat(result.username()).isEqualTo(username);
        assertThat(result.role()).isEqualTo("COMPLIANCE_OFFICER");

        verify(userRepository).findByUsername(username);
        verify(passwordEncoder).matches(password, testUser.getPasswordHash());
        verify(jwtTokenService).generateAccessToken(any(User.class), anyString());
        verify(jwtTokenService).generateRefreshToken();
        verify(refreshTokenRepository).save(any(UUID.class), eq(testUserId), eq(refreshTokenHash), any(LocalDateTime.class));
        verify(userRepository).save(testUser);
    }

    @Test
    @DisplayName("Should throw exception when user not found")
    void shouldThrowExceptionWhenUserNotFound() {
        // Given
        String username = "nonexistent@abc.nl";
        String password = "password";

        when(userRepository.findByUsername(username)).thenReturn(Optional.empty());

        // When / Then
        assertThatThrownBy(() -> authenticationService.authenticate(username, password))
                .isInstanceOf(AuthenticationService.AuthenticationException.class)
                .hasMessage("Invalid username or password");

        verify(userRepository).findByUsername(username);
        verify(passwordEncoder, never()).matches(anyString(), anyString());
    }

    @Test
    @DisplayName("Should throw exception when password is incorrect")
    void shouldThrowExceptionWhenPasswordIncorrect() {
        // Given
        String username = "officer@abc.nl";
        String wrongPassword = "WrongPassword123!";

        when(userRepository.findByUsername(username)).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches(wrongPassword, testUser.getPasswordHash())).thenReturn(false);

        // When / Then
        assertThatThrownBy(() -> authenticationService.authenticate(username, wrongPassword))
                .isInstanceOf(AuthenticationService.AuthenticationException.class)
                .hasMessage("Invalid username or password");

        verify(userRepository).findByUsername(username);
        verify(passwordEncoder).matches(wrongPassword, testUser.getPasswordHash());
        verify(jwtTokenService, never()).generateAccessToken(any(), anyString());
    }

    @Test
    @DisplayName("Should throw exception when user is inactive")
    void shouldThrowExceptionWhenUserInactive() {
        // Given
        String username = "officer@abc.nl";
        String password = "Officer123!";

        testUser.deactivate();
        when(userRepository.findByUsername(username)).thenReturn(Optional.of(testUser));

        // When / Then
        assertThatThrownBy(() -> authenticationService.authenticate(username, password))
                .isInstanceOf(AuthenticationService.AuthenticationException.class)
                .hasMessage("User account is inactive");

        verify(userRepository).findByUsername(username);
        verify(passwordEncoder, never()).matches(anyString(), anyString());
    }

    @Test
    @DisplayName("Should update last login timestamp on successful authentication")
    void shouldUpdateLastLoginTimestamp() {
        // Given
        String username = "officer@abc.nl";
        String password = "Officer123!";

        when(userRepository.findByUsername(username)).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches(password, testUser.getPasswordHash())).thenReturn(true);
        when(jwtTokenService.generateAccessToken(any(User.class), anyString())).thenReturn("token");
        when(jwtTokenService.generateRefreshToken()).thenReturn(
                new JwtTokenService.RefreshTokenPair("refresh", "hash")
        );
        when(jwtTokenService.getRefreshTokenExpiry()).thenReturn(LocalDateTime.now().plusDays(30));
        when(jwtTokenService.getRefreshTokenExpiryMillis()).thenReturn(2592000000L);
        when(userRepository.save(any(User.class))).thenReturn(testUser);

        // When
        authenticationService.authenticate(username, password);

        // Then
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User savedUser = userCaptor.getValue();
        assertThat(savedUser.getLastLoginAt()).isNotNull();
        assertThat(savedUser.getLastLoginAt()).isAfter(LocalDateTime.now().minusMinutes(1));
    }

    // ==================== refreshAccessToken() Tests ====================

    @Test
    @DisplayName("Should successfully refresh access token with valid refresh token")
    void shouldRefreshTokenWithValidRefreshToken() {
        // Given
        String refreshToken = "refresh-token-123";
        String refreshTokenHash = "refresh-token-hash";
        String newAccessToken = "new-access-token";
        String newRefreshToken = "new-refresh-token";
        String newRefreshTokenHash = "new-refresh-token-hash";

        RefreshTokenRepository.RefreshToken storedToken = new RefreshTokenRepository.RefreshToken(
                UUID.randomUUID(),
                testUserId,
                refreshTokenHash,
                LocalDateTime.now().plusDays(30),
                false,
                LocalDateTime.now()
        );

        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(storedToken));
        when(refreshTokenRepository.isTokenValid(anyString())).thenReturn(true);
        when(userRepository.findById(testUserId)).thenReturn(Optional.of(testUser));
        when(jwtTokenService.generateAccessToken(any(User.class), anyString())).thenReturn(newAccessToken);
        when(jwtTokenService.generateRefreshToken()).thenReturn(
                new JwtTokenService.RefreshTokenPair(newRefreshToken, newRefreshTokenHash)
        );
        when(jwtTokenService.getRefreshTokenExpiry()).thenReturn(LocalDateTime.now().plusDays(30));
        when(jwtTokenService.getRefreshTokenExpiryMillis()).thenReturn(2592000000L);

        // When
        AuthenticationService.RefreshResult result = authenticationService.refreshAccessToken(refreshToken);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.accessToken()).isEqualTo(newAccessToken);
        assertThat(result.refreshToken()).isEqualTo(newRefreshToken);

        verify(refreshTokenRepository).findByTokenHash(anyString());
        verify(refreshTokenRepository).revokeToken(anyString());
        verify(jwtTokenService).generateAccessToken(any(User.class), anyString());
        verify(jwtTokenService).generateRefreshToken();
        verify(refreshTokenRepository).save(any(UUID.class), eq(testUserId), eq(newRefreshTokenHash), any(LocalDateTime.class));
    }

    @Test
    @DisplayName("Should throw exception when refresh token not found")
    void shouldThrowExceptionWhenRefreshTokenNotFound() {
        // Given
        String refreshToken = "invalid-refresh-token";

        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());

        // When / Then
        assertThatThrownBy(() -> authenticationService.refreshAccessToken(refreshToken))
                .isInstanceOf(AuthenticationService.AuthenticationException.class)
                .hasMessage("Invalid refresh token");

        verify(refreshTokenRepository).findByTokenHash(anyString());
        verify(refreshTokenRepository, never()).revokeToken(anyString());
    }

    @Test
    @DisplayName("Should throw exception when refresh token is revoked")
    void shouldThrowExceptionWhenRefreshTokenRevoked() {
        // Given
        String refreshToken = "revoked-token";
        String refreshTokenHash = "revoked-token-hash";

        RefreshTokenRepository.RefreshToken storedToken = new RefreshTokenRepository.RefreshToken(
                UUID.randomUUID(),
                testUserId,
                refreshTokenHash,
                LocalDateTime.now().plusDays(30),
                true, // Revoked
                LocalDateTime.now()
        );

        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(storedToken));
        when(refreshTokenRepository.isTokenValid(anyString())).thenReturn(false);

        // When / Then
        assertThatThrownBy(() -> authenticationService.refreshAccessToken(refreshToken))
                .isInstanceOf(AuthenticationService.AuthenticationException.class)
                .hasMessage("Refresh token is invalid or expired");

        verify(refreshTokenRepository).findByTokenHash(anyString());
        verify(refreshTokenRepository).isTokenValid(anyString());
        verify(refreshTokenRepository, never()).revokeToken(anyString());
    }

    @Test
    @DisplayName("Should throw exception when refresh token expired")
    void shouldThrowExceptionWhenRefreshTokenExpired() {
        // Given
        String refreshToken = "expired-token";
        String refreshTokenHash = "expired-token-hash";

        RefreshTokenRepository.RefreshToken storedToken = new RefreshTokenRepository.RefreshToken(
                UUID.randomUUID(),
                testUserId,
                refreshTokenHash,
                LocalDateTime.now().minusDays(1), // Expired
                false,
                LocalDateTime.now()
        );

        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(storedToken));
        when(refreshTokenRepository.isTokenValid(anyString())).thenReturn(false);

        // When / Then
        assertThatThrownBy(() -> authenticationService.refreshAccessToken(refreshToken))
                .isInstanceOf(AuthenticationService.AuthenticationException.class)
                .hasMessage("Refresh token is invalid or expired");
    }

    @Test
    @DisplayName("Should throw exception when user no longer exists")
    void shouldThrowExceptionWhenUserNoLongerExists() {
        // Given
        String refreshToken = "valid-token";
        String refreshTokenHash = "valid-token-hash";

        RefreshTokenRepository.RefreshToken storedToken = new RefreshTokenRepository.RefreshToken(
                UUID.randomUUID(),
                testUserId,
                refreshTokenHash,
                LocalDateTime.now().plusDays(30),
                false,
                LocalDateTime.now()
        );

        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(storedToken));
        when(refreshTokenRepository.isTokenValid(anyString())).thenReturn(true);
        when(userRepository.findById(testUserId)).thenReturn(Optional.empty());

        // When / Then
        assertThatThrownBy(() -> authenticationService.refreshAccessToken(refreshToken))
                .isInstanceOf(AuthenticationService.AuthenticationException.class)
                .hasMessage("User not found");
    }

    @Test
    @DisplayName("Should throw exception when user became inactive")
    void shouldThrowExceptionWhenUserBecameInactive() {
        // Given
        String refreshToken = "valid-token";
        String refreshTokenHash = "valid-token-hash";

        testUser.deactivate();

        RefreshTokenRepository.RefreshToken storedToken = new RefreshTokenRepository.RefreshToken(
                UUID.randomUUID(),
                testUserId,
                refreshTokenHash,
                LocalDateTime.now().plusDays(30),
                false,
                LocalDateTime.now()
        );

        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(storedToken));
        when(refreshTokenRepository.isTokenValid(anyString())).thenReturn(true);
        when(userRepository.findById(testUserId)).thenReturn(Optional.of(testUser));

        // When / Then
        assertThatThrownBy(() -> authenticationService.refreshAccessToken(refreshToken))
                .isInstanceOf(AuthenticationService.AuthenticationException.class)
                .hasMessage("User account is inactive");
    }

    @Test
    @DisplayName("Should revoke old refresh token when rotating (token rotation)")
    void shouldRevokeOldRefreshTokenWhenRotating() {
        // Given
        String refreshToken = "old-refresh-token";
        String refreshTokenHash = "old-refresh-token-hash";

        RefreshTokenRepository.RefreshToken storedToken = new RefreshTokenRepository.RefreshToken(
                UUID.randomUUID(),
                testUserId,
                refreshTokenHash,
                LocalDateTime.now().plusDays(30),
                false,
                LocalDateTime.now()
        );

        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(storedToken));
        when(refreshTokenRepository.isTokenValid(anyString())).thenReturn(true);
        when(userRepository.findById(testUserId)).thenReturn(Optional.of(testUser));
        when(jwtTokenService.generateAccessToken(any(User.class), anyString())).thenReturn("new-token");
        when(jwtTokenService.generateRefreshToken()).thenReturn(
                new JwtTokenService.RefreshTokenPair("new-refresh", "new-hash")
        );
        when(jwtTokenService.getRefreshTokenExpiry()).thenReturn(LocalDateTime.now().plusDays(30));
        when(jwtTokenService.getRefreshTokenExpiryMillis()).thenReturn(2592000000L);

        // When
        authenticationService.refreshAccessToken(refreshToken);

        // Then
        verify(refreshTokenRepository).revokeToken(anyString());
    }

    // ==================== logout() Tests ====================

    @Test
    @DisplayName("Should successfully logout and revoke all user tokens")
    void shouldLogoutAndRevokeAllTokens() {
        // Given
        UUID userId = UUID.randomUUID();

        // When
        authenticationService.logout(userId);

        // Then
        verify(refreshTokenRepository).revokeAllUserTokens(userId);
    }

    // ==================== getUserById() Tests ====================

    @Test
    @DisplayName("Should get user by ID when user exists")
    void shouldGetUserById() {
        // Given
        when(userRepository.findById(testUserId)).thenReturn(Optional.of(testUser));

        // When
        Optional<User> result = authenticationService.getUserById(testUserId);

        // Then
        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo(testUser);
        verify(userRepository).findById(testUserId);
    }

    @Test
    @DisplayName("Should return empty when user not found by ID")
    void shouldReturnEmptyWhenUserNotFoundById() {
        // Given
        UUID nonExistentId = UUID.randomUUID();
        when(userRepository.findById(nonExistentId)).thenReturn(Optional.empty());

        // When
        Optional<User> result = authenticationService.getUserById(nonExistentId);

        // Then
        assertThat(result).isEmpty();
        verify(userRepository).findById(nonExistentId);
    }

    // ==================== getUserByUsername() Tests ====================

    @Test
    @DisplayName("Should get user by username when user exists")
    void shouldGetUserByUsername() {
        // Given
        String username = "officer@abc.nl";
        when(userRepository.findByUsername(username)).thenReturn(Optional.of(testUser));

        // When
        Optional<User> result = authenticationService.getUserByUsername(username);

        // Then
        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo(testUser);
        verify(userRepository).findByUsername(username);
    }

    @Test
    @DisplayName("Should return empty when user not found by username")
    void shouldReturnEmptyWhenUserNotFoundByUsername() {
        // Given
        String nonExistentUsername = "nonexistent@abc.nl";
        when(userRepository.findByUsername(nonExistentUsername)).thenReturn(Optional.empty());

        // When
        Optional<User> result = authenticationService.getUserByUsername(nonExistentUsername);

        // Then
        assertThat(result).isEmpty();
        verify(userRepository).findByUsername(nonExistentUsername);
    }
}
