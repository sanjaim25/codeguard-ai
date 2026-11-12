package com.codeguard.common;

public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException() { super("Resource not found or access has expired"); }
}
