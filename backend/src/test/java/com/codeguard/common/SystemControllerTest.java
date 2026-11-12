package com.codeguard.common;

import com.codeguard.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

@WebMvcTest(SystemController.class)
@Import(SecurityConfig.class)
class SystemControllerTest {
    @Autowired MockMvc mvc;
    @org.springframework.test.context.bean.override.mockito.MockitoBean java.time.Clock clock;
    @org.junit.jupiter.api.BeforeEach void fixedWindow() {
        org.mockito.Mockito.when(clock.instant()).thenReturn(java.time.Instant.ofEpochSecond(121));
    }

    @Test void identifiesDemoModeWithoutAuthentication() throws Exception {
        mvc.perform(get("/api/system")).andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("CodeGuard AI"))
            .andExpect(jsonPath("$.demo").value(false));
    }
    @Test void issuesCsrfTokenAndProtectsMutations() throws Exception {
        mvc.perform(get("/api/csrf")).andExpect(status().isOk()).andExpect(jsonPath("$.token").isNotEmpty());
        mvc.perform(post("/api/logout")).andExpect(status().isForbidden());
    }
    @Test void deniesAnonymousResourceAccess() throws Exception {
        mvc.perform(get("/api/repositories")).andExpect(status().isUnauthorized());
    }
    @Test void unknownAuthenticatedRoutesReturn404InsteadOfInternalErrors() throws Exception {
        mvc.perform(get("/api/does-not-exist").with(user("test"))).andExpect(status().isNotFound());
    }
    @Test void securityChainThrottlesAuthenticatedGithubSynchronization() throws Exception {
        for (int i = 0; i < 12; i++)
            mvc.perform(post("/api/github/sync").with(user("rate-limit-user")).with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isNotFound()); // No GitHub controller in this MVC slice.
        mvc.perform(post("/api/github/sync").with(user("rate-limit-user")).with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf()))
            .andExpect(status().isTooManyRequests()).andExpect(header().exists("Retry-After"))
            .andExpect(jsonPath("$.status").value(429));
    }
}
