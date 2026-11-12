package com.codeguard.github;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class InstallationTokensTest {
    @Test void cachesUnexpiredTokensAndRefreshesAfterInvalidation() throws Exception {
        var github = mock(GithubClient.class); var signer = mock(AppJwtSigner.class);
        when(signer.sign()).thenReturn("test-jwt");
        when(github.post(anyString(), anyString(), any())).thenReturn(new ObjectMapper().readTree("{\"token\":\"test-installation-token\",\"expires_at\":\"2026-01-01T01:00:00Z\"}"));
        var tokens = new InstallationTokens(github, signer, Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));
        assertThat(tokens.get(1)).isEqualTo("test-installation-token"); tokens.get(1);
        verify(github, times(1)).post(anyString(), anyString(), any());
        tokens.invalidate(1); tokens.get(1);
        verify(github, times(2)).post(anyString(), anyString(), any());
    }
    @Test void rejectsAlreadyExpiredProviderTokens() throws Exception {
        var github = mock(GithubClient.class); var signer = mock(AppJwtSigner.class);
        when(signer.sign()).thenReturn("test-jwt");
        when(github.post(anyString(), anyString(), any())).thenReturn(new ObjectMapper().readTree("{\"token\":\"old\",\"expires_at\":\"2025-01-01T00:00:00Z\"}"));
        var tokens = new InstallationTokens(github, signer, Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));
        assertThatThrownBy(() -> tokens.get(1)).isInstanceOf(GithubApiException.class);
    }
}
