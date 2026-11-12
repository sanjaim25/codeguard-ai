package com.codeguard.github;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class WebhookSignatures {
    private final byte[] secret;
    public WebhookSignatures(@Value("${codeguard.github.webhook-secret:}") String secret) { this.secret = secret.getBytes(StandardCharsets.UTF_8); }
    public boolean verify(byte[] originalBody, String signature) {
        if (secret.length == 0 || signature == null || !signature.matches("sha256=[0-9a-fA-F]{64}")) return false;
        try {
            var mac = Mac.getInstance("HmacSHA256"); mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return MessageDigest.isEqual(mac.doFinal(originalBody), HexFormat.of().parseHex(signature.substring(7)));
        } catch (GeneralSecurityException unsupported) { throw new IllegalStateException("HMAC-SHA256 is unavailable"); }
    }
}
