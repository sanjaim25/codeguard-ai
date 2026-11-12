package com.codeguard.github;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class WebhookSignaturesTest {
    @Test void validatesRfc4231VectorAgainstOriginalBytes() {
        var signatures = new WebhookSignatures("Jefe");
        String header = "sha256=5bdcc146bf60754e6a042426089575c75a003f089d2739839dec58b964ec3843";
        byte[] body = "what do ya want for nothing?".getBytes(StandardCharsets.UTF_8);
        assertThat(signatures.verify(body, header)).isTrue();
        assertThat(signatures.verify("what do ya want for nothing?\n".getBytes(StandardCharsets.UTF_8), header)).isFalse();
        assertThat(signatures.verify(body, null)).isFalse();
        assertThat(signatures.verify(body, "sha256=invalid")).isFalse();
        assertThat(new WebhookSignatures("").verify(body, header)).isFalse();
    }
}
