package com.codeguard.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import org.springframework.security.oauth2.client.registration.*;
import org.springframework.security.oauth2.client.web.*;
import org.springframework.security.oauth2.core.*;

@Configuration
@ConditionalOnProperty(name = "codeguard.github.auth-enabled", havingValue = "true")
public class GithubOAuthConfig {
    @Bean ClientRegistrationRepository clientRegistrations(@Value("${codeguard.github.client-id}") String id,
                                                           @Value("${codeguard.github.client-secret}") String secret) {
        if (id.isBlank() || secret.isBlank()) throw new IllegalStateException("GitHub OAuth credentials are required when authentication is enabled");
        return new InMemoryClientRegistrationRepository(ClientRegistration.withRegistrationId("github")
            .clientId(id).clientSecret(secret).clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
            .authorizationUri("https://github.com/login/oauth/authorize").tokenUri("https://github.com/login/oauth/access_token")
            .userInfoUri("https://api.github.com/user").userNameAttributeName("id").clientName("GitHub").build());
    }
    @Bean OAuth2AuthorizedClientRepository authorizedClients() { return new HttpSessionOAuth2AuthorizedClientRepository(); }
}
