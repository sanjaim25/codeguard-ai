package com.codeguard.github;

import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jwt.SignedJWT;
import java.nio.file.*;
import java.security.*;
import java.security.interfaces.RSAPublicKey;
import java.time.*;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

class AppJwtSignerTest {
    @TempDir Path directory;
    @Test void producesVerifiableShortLivedRs256Token() throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA"); generator.initialize(2048); var pair = generator.generateKeyPair();
        var path = directory.resolve("test-key.pem");
        Files.writeString(path, "-----BEGIN PRIVATE KEY-----\n" + Base64.getEncoder().encodeToString(pair.getPrivate().getEncoded()) + "\n-----END PRIVATE KEY-----");
        var now = Instant.parse("2026-01-01T00:00:00Z");
        var signer = new AppJwtSigner("1234", path.toString(), Clock.fixed(now, ZoneOffset.UTC));
        var jwt = SignedJWT.parse(signer.sign());
        assertThat(jwt.verify(new RSASSAVerifier((RSAPublicKey) pair.getPublic()))).isTrue();
        assertThat(jwt.getJWTClaimsSet().getIssuer()).isEqualTo("1234");
        assertThat(jwt.getJWTClaimsSet().getIssueTime().toInstant()).isEqualTo(now.minusSeconds(60));
        assertThat(jwt.getJWTClaimsSet().getExpirationTime().toInstant()).isEqualTo(now.plusSeconds(480));
    }
    @Test void refusesMissingConfiguration() {
        assertThatThrownBy(() -> new AppJwtSigner("", "", Clock.systemUTC()).sign()).isInstanceOf(IllegalStateException.class);
    }
}
