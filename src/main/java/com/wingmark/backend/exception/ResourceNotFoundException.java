package com.wingmark.backend.exception;

/** Thrown when a resource does not exist or is not the caller's. */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }

    public static ResourceNotFoundException of(String resource, Object identifier) {
        return new ResourceNotFoundException(resource + " not found with id: " + identifier);
    }
}
