package com.abcbank.onboarding.domain.exception;

/**
 * Exception thrown when a requested resource is not found.
 */
public class ResourceNotFoundException extends DomainException {

    private final String resourceType;
    private final String resourceId;

    public ResourceNotFoundException(String resourceType, String resourceId) {
        super(String.format("%s with ID %s not found", resourceType, resourceId));
        this.resourceType = resourceType;
        this.resourceId = resourceId;
    }

    public String getResourceType() {
        return resourceType;
    }

    public String getResourceId() {
        return resourceId;
    }
}
