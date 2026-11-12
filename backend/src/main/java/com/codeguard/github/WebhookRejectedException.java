package com.codeguard.github;
import org.springframework.http.HttpStatus;
public class WebhookRejectedException extends RuntimeException {
    private final HttpStatus status;
    public WebhookRejectedException(HttpStatus status, String safeReason) { super(safeReason); this.status = status; }
    public HttpStatus status() { return status; }
}
