package com.codeguard.auth;
public class AuthenticationExpiredException extends RuntimeException {
    public AuthenticationExpiredException() { super("Please sign in with GitHub again"); }
}
