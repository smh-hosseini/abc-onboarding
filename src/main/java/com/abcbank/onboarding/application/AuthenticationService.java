package com.abcbank.onboarding.application;

import com.abcbank.onboarding.domain.model.User;
import com.abcbank.onboarding.domain.port.out.RefreshTokenRepository;
import com.abcbank.onboarding.domain.port.out.UserRepository;
import com.abcbank.onboarding.infrastructure.security.JwtTokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Authentication Service for internal users (username/password authentication).
 * Handles login, token generation, refresh token rotation, and logout.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthenticationService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtTokenService jwtTokenService;
    private final PasswordEncoder passwordEncoder;

    /**
     * Authenticate user with username and password.
     * Generates access token and refresh token.
     */
    @Transactional
    public AuthenticationResult authenticate(String username, String password) {
        log.info("Authentication attempt for user: {}", username);

        // Find user by username
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> {
                    log.warn("Authentication failed - user not found: {}", username);
                    return new AuthenticationException("Invalid username or password");
                });

        // Check if user is active
        if (!user.isActive()) {
            log.warn("Authentication failed - user is inactive: {}", username);
            throw new AuthenticationException("User account is inactive");
        }

        // Verify password
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            log.warn("Authentication failed - invalid password for user: {}", username);
            throw new AuthenticationException("Invalid username or password");
        }

        // Update last login timestamp
        user.recordLogin();
        userRepository.save(user);

        // Generate session ID
        String sessionId = UUID.randomUUID().toString();

        // Generate access token
        String accessToken = jwtTokenService.generateAccessToken(user, sessionId);

        // Generate refresh token
        JwtTokenService.RefreshTokenPair refreshTokenPair = jwtTokenService.generateRefreshToken();

        // Store refresh token hash in database
        refreshTokenRepository.save(
                UUID.randomUUID(),
                user.getId(),
                refreshTokenPair.tokenHash(),
                jwtTokenService.getRefreshTokenExpiry()
        );

        log.info("Authentication successful for user: {}", username);

        return new AuthenticationResult(
                accessToken,
                refreshTokenPair.token(),
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getRole().name(),
                jwtTokenService.getRefreshTokenExpiryMillis()
        );
    }

    /**
     * Refresh access token using refresh token.
     * Implements refresh token rotation - old token is revoked, new one is issued.
     */
    @Transactional
    public RefreshResult refreshAccessToken(String refreshToken) {
        log.info("Refresh token request received");

        // Hash the provided refresh token
        String tokenHash = hashRefreshToken(refreshToken);

        // Find and validate refresh token
        RefreshTokenRepository.RefreshToken storedToken = refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> {
                    log.warn("Refresh failed - token not found");
                    return new AuthenticationException("Invalid refresh token");
                });

        // Check if token is valid (not revoked, not expired)
        if (!refreshTokenRepository.isTokenValid(tokenHash)) {
            log.warn("Refresh failed - token is invalid or revoked");
            throw new AuthenticationException("Refresh token is invalid or expired");
        }

        // Get user
        User user = userRepository.findById(storedToken.userId())
                .orElseThrow(() -> {
                    log.error("Refresh failed - user not found: {}", storedToken.userId());
                    return new AuthenticationException("User not found");
                });

        // Check if user is still active
        if (!user.isActive()) {
            log.warn("Refresh failed - user is inactive: {}", user.getUsername());
            throw new AuthenticationException("User account is inactive");
        }

        // Revoke old refresh token (rotation)
        refreshTokenRepository.revokeToken(tokenHash);

        // Generate new session ID
        String sessionId = UUID.randomUUID().toString();

        // Generate new access token
        String accessToken = jwtTokenService.generateAccessToken(user, sessionId);

        // Generate new refresh token
        JwtTokenService.RefreshTokenPair newRefreshTokenPair = jwtTokenService.generateRefreshToken();

        // Store new refresh token hash
        refreshTokenRepository.save(
                UUID.randomUUID(),
                user.getId(),
                newRefreshTokenPair.tokenHash(),
                jwtTokenService.getRefreshTokenExpiry()
        );

        log.info("Refresh successful for user: {}", user.getUsername());

        return new RefreshResult(
                accessToken,
                newRefreshTokenPair.token(),
                jwtTokenService.getRefreshTokenExpiryMillis()
        );
    }

    /**
     * Logout user by revoking all their refresh tokens.
     */
    @Transactional
    public void logout(UUID userId) {
        log.info("Logout request for user: {}", userId);

        refreshTokenRepository.revokeAllUserTokens(userId);

        log.info("Logout successful - all tokens revoked for user: {}", userId);
    }

    /**
     * Get user by ID.
     */
    @Transactional(readOnly = true)
    public Optional<User> getUserById(UUID userId) {
        return userRepository.findById(userId);
    }

    /**
     * Get user by username.
     */
    @Transactional(readOnly = true)
    public Optional<User> getUserByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    /**
     * Hash refresh token using SHA-256 (same algorithm as JwtTokenService).
     */
    private String hashRefreshToken(String token) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.Base64.getEncoder().encodeToString(hash);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    /**
     * Result of successful authentication.
     */
    public record AuthenticationResult(
            String accessToken,
            String refreshToken,
            UUID userId,
            String username,
            String email,
            String role,
            long refreshTokenExpiryMillis
    ) {}

    /**
     * Result of successful token refresh.
     */
    public record RefreshResult(
            String accessToken,
            String refreshToken,
            long refreshTokenExpiryMillis
    ) {}

    /**
     * Exception thrown when authentication fails.
     */
    public static class AuthenticationException extends RuntimeException {
        public AuthenticationException(String message) {
            super(message);
        }
    }
}
