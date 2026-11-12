package com.codeguard.auth;

import com.codeguard.common.SystemController;
import com.codeguard.config.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(SystemController.class)
@Import({SecurityConfig.class, GithubOAuthConfig.class})
@TestPropertySource(properties = {"codeguard.github.auth-enabled=true", "codeguard.github.client-id=test-client", "codeguard.github.client-secret=test-secret"})
class OAuthSecurityTest {
    @Autowired MockMvc mvc;
    @MockitoBean GithubOAuthUserService users;
    @Test void authorizationRedirectContainsStateAndServerCallback() throws Exception {
        mvc.perform(get("/oauth2/authorization/github")).andExpect(status().is3xxRedirection())
            .andExpect(header().string("Location", startsWith("https://github.com/login/oauth/authorize?")))
            .andExpect(header().string("Location", containsString("state=")))
            .andExpect(header().string("Location", containsString("client_id=test-client")))
            .andExpect(header().string("Location", not(containsString("test-secret"))));
    }
    @Test void forgedCallbackCannotCreateAuthenticatedSession() throws Exception {
        mvc.perform(get("/login/oauth2/code/github?code=forged&state=unrecognized"))
            .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/signin?error=github"));
        verifyNoInteractions(users);
    }
    @Test void authenticatedLogoutStillRequiresCsrf() throws Exception {
        mvc.perform(post("/api/logout").with(oauth2Login())).andExpect(status().isForbidden());
        mvc.perform(post("/api/logout").with(oauth2Login()).with(csrf())).andExpect(status().isNoContent());
    }
}
