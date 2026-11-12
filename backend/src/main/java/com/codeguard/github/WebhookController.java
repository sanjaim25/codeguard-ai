package com.codeguard.github;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
public class WebhookController {
    static final int MAX_BODY_BYTES = 2_000_000;
    private final WebhookSignatures signatures;
    private final WebhookService service;
    public WebhookController(WebhookSignatures signatures, WebhookService service) { this.signatures = signatures; this.service = service; }
    @PostMapping("/api/webhooks/github")
    public ResponseEntity<WebhookService.Receipt> receive(HttpServletRequest request) throws java.io.IOException {
        if (request.getContentLengthLong() > MAX_BODY_BYTES) throw new WebhookRejectedException(HttpStatus.PAYLOAD_TOO_LARGE, "Webhook body exceeds the configured limit");
        byte[] body = request.getInputStream().readNBytes(MAX_BODY_BYTES + 1);
        if (body.length > MAX_BODY_BYTES) throw new WebhookRejectedException(HttpStatus.PAYLOAD_TOO_LARGE, "Webhook body exceeds the configured limit");
        if (!signatures.verify(body, request.getHeader("X-Hub-Signature-256"))) throw new WebhookRejectedException(HttpStatus.UNAUTHORIZED, "Invalid webhook signature");
        String delivery = request.getHeader("X-GitHub-Delivery"), event = request.getHeader("X-GitHub-Event");
        if (delivery == null || !delivery.matches("[a-fA-F0-9-]{36}") || event == null || !event.matches("[a-z_]{1,50}"))
            throw new WebhookRejectedException(HttpStatus.BAD_REQUEST, "Invalid webhook headers");
        return ResponseEntity.accepted().body(service.processVerified(java.util.UUID.fromString(delivery).toString(), event, body));
    }
}
