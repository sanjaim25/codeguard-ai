package com.codeguard.security;

import java.time.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class RequestRateLimiterTest {
    @Test void boundsEachIdentityAndReclaimsExpiredCapacity() {
        Clock clock = mock(Clock.class);
        when(clock.instant()).thenReturn(Instant.ofEpochSecond(121));
        var limiter = new RequestRateLimiter(clock, 2);
        assertThat(limiter.acquire("one", 1)).isZero();
        assertThat(limiter.acquire("one", 1)).isEqualTo(59);
        assertThat(limiter.acquire("two", 1)).isZero();
        assertThat(limiter.acquire("three", 1)).isEqualTo(59);
        when(clock.instant()).thenReturn(Instant.ofEpochSecond(180));
        assertThat(limiter.acquire("three", 1)).isZero();
        assertThat(limiter.acquire("one", 1)).isZero();
    }
    @Test void limitsExpensiveOperationsByUserAndReturnsSafeRetryResponse() throws Exception {
        var filter = new RequestRateLimitFilter(new RequestRateLimiter(Clock.fixed(Instant.ofEpochSecond(121), ZoneOffset.UTC), 100));
        var request = new MockHttpServletRequest("POST", "/api/github/sync");
        request.setServletPath("/api/github/sync");
        var chain = mock(jakarta.servlet.FilterChain.class);
        try {
            SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated("user-1", "secret", java.util.List.of()));
            for (int i = 0; i < 12; i++) filter.doFilter(request, new MockHttpServletResponse(), chain);
            var denied = new MockHttpServletResponse(); filter.doFilter(request, denied, chain);
            assertThat(denied.getStatus()).isEqualTo(429);
            assertThat(denied.getHeader("Retry-After")).isEqualTo("59");
            assertThat(denied.getContentAsString()).doesNotContain("secret", "user-1");
            verify(chain, times(12)).doFilter(any(), any());
            SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated("user-2", null, java.util.List.of()));
            var allowed = new MockHttpServletResponse(); filter.doFilter(request, allowed, chain);
            assertThat(allowed.getStatus()).isEqualTo(200);
            request.setServletPath("/actuator/health");
            filter.doFilter(request, new MockHttpServletResponse(), chain);
            verify(chain, times(14)).doFilter(any(), any());
        } finally { SecurityContextHolder.clearContext(); }
    }
}
