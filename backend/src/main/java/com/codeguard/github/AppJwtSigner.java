package com.codeguard.github;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.*;
import java.nio.file.*;
import java.security.*;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Clock;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class AppJwtSigner {
    private final String issuer;
    private final String keyPath;
    private final Clock clock;
    public AppJwtSigner(@Value("${codeguard.github.app-id:}") String issuer,
                        @Value("${codeguard.github.private-key-path:}") String keyPath, Clock clock) {
        this.issuer = issuer; this.keyPath = keyPath; this.clock = clock;
    }
    public String sign() {
        if (issuer.isBlank() || keyPath.isBlank()) throw new IllegalStateException("GitHub App credentials are not configured");
        try {
            String pem = Files.readString(Path.of(keyPath));
            if (!pem.contains("-----BEGIN PRIVATE KEY-----")) throw new IllegalStateException("GitHub key must be unencrypted PKCS8");
            byte[] der = Base64.getMimeDecoder().decode(pem.replace("-----BEGIN PRIVATE KEY-----", "").replace("-----END PRIVATE KEY-----", ""));
            var key = (RSAPrivateKey) KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
            var now = clock.instant();
            var jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), new JWTClaimsSet.Builder().issuer(issuer)
                .issueTime(Date.from(now.minusSeconds(60))).expirationTime(Date.from(now.plusSeconds(480))).build());
            jwt.sign(new RSASSASigner(key)); return jwt.serialize();
        } catch (java.io.IOException | GeneralSecurityException | JOSEException invalidKey) {
            throw new IllegalStateException("Unable to load or use GitHub App signing key");
        }
    }
}
