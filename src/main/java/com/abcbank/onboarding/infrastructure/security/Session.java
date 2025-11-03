package com.abcbank.onboarding.infrastructure.security;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Session model for COMPLIANCE_OFFICER and ADMIN users
 * Stored in Redis for distributed session management
 */
public class Session implements Serializable {

    private static final long serialVersionUID = 1L;

    private String sessionId;
    private UUID userId;
    private String employeeId;
    private String email;
    private String[] roles;
    private String deviceFingerprint;
    private String ipAddress;
    private String userAgent;
    private LocalDateTime createdAt;
    private LocalDateTime lastActivityAt;
    private LocalDateTime expiresAt;
    private boolean active;

    public Session() {
    }

    public Session(String sessionId, UUID userId, String employeeId, String email,
                   String[] roles, String ipAddress, String userAgent, LocalDateTime expiresAt) {
        this.sessionId = sessionId;
        this.userId = userId;
        this.employeeId = employeeId;
        this.email = email;
        this.roles = roles;
        this.ipAddress = ipAddress;
        this.userAgent = userAgent;
        this.createdAt = LocalDateTime.now();
        this.lastActivityAt = LocalDateTime.now();
        this.expiresAt = expiresAt;
        this.active = true;
        this.deviceFingerprint = generateFingerprint(ipAddress, userAgent);
    }

    private String generateFingerprint(String ip, String ua) {
        return "fp_" + Math.abs((ip + ua).hashCode());
    }

    // Getters and Setters

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public String getEmployeeId() {
        return employeeId;
    }

    public void setEmployeeId(String employeeId) {
        this.employeeId = employeeId;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String[] getRoles() {
        return roles;
    }

    public void setRoles(String[] roles) {
        this.roles = roles;
    }

    public String getDeviceFingerprint() {
        return deviceFingerprint;
    }

    public void setDeviceFingerprint(String deviceFingerprint) {
        this.deviceFingerprint = deviceFingerprint;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getLastActivityAt() {
        return lastActivityAt;
    }

    public void setLastActivityAt(LocalDateTime lastActivityAt) {
        this.lastActivityAt = lastActivityAt;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public void updateActivity() {
        this.lastActivityAt = LocalDateTime.now();
    }

    @JsonIgnore
    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiresAt);
    }

    @JsonIgnore
    public boolean isIdleTimeout(long idleTimeoutMillis) {
        LocalDateTime idleThreshold = LocalDateTime.now().minusNanos(idleTimeoutMillis * 1_000_000);
        return lastActivityAt.isBefore(idleThreshold);
    }
}
