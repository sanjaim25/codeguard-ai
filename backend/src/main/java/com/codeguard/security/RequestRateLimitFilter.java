package com.codeguard.security;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public final class RequestRateLimitFilter extends OncePerRequestFilter {
    private final RequestRateLimiter limiter;
    public RequestRateLimitFilter(RequestRateLimiter limiter) { this.limiter = limiter; }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws ServletException, IOException {
        String path = request.getServletPath();
        if (path.isEmpty()) path = request.getRequestURI().substring(request.getContextPath().length());
        if (path.equals("/actuator/health")) { chain.doFilter(request, response); return; }
        var auth = SecurityContextHolder.getContext().getAuthentication();
        boolean signedIn = auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken);
        String identity = signedIn ? "user:" + auth.getName() : "peer:" + request.getRemoteAddr();
        String scope = "api";
        int limit = signedIn ? 1200 : 300;
        if (path.equals("/api/github/sync")) { scope = "sync"; limit = 12; }
        else if (path.matches("/api/reviews/[^/]+/publication(?:/reconcile)?") && request.getMethod().equals("POST")) { scope = "publish"; limit = 12; }
        else if (path.equals("/api/auth/demo")) { scope = "demo"; limit = 30; }
        long retryAfter = limiter.acquire(scope + ":" + identity, limit);
        if (retryAfter == 0) { chain.doFilter(request, response); return; }
        response.setStatus(429);
        response.setHeader("Retry-After", Long.toString(retryAfter));
        response.setHeader("Cache-Control", "no-store");
        response.setContentType("application/problem+json");
        response.getWriter().write("{\"type\":\"about:blank\",\"title\":\"Too Many Requests\",\"status\":429,\"detail\":\"Request limit reached. Wait before trying again.\"}");
    }
}
