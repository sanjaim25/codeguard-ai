package com.codeguard.auth;

import com.codeguard.github.*;
import com.codeguard.user.UserAccountRepository;
import java.util.*;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.userinfo.*;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.core.user.*;
import org.springframework.stereotype.Service;

@Service
public class GithubOAuthUserService implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {
    private final GithubDiscovery discovery;
    private final UserAccountRepository users;
    public GithubOAuthUserService(GithubDiscovery discovery, UserAccountRepository users) { this.discovery = discovery; this.users = users; }
    @Override public OAuth2User loadUser(OAuth2UserRequest request) {
        try {
            var synced = discovery.synchronize(request.getAccessToken().getTokenValue());
            var user = users.findById(synced.userId()).orElseThrow();
            return new DefaultOAuth2User(List.of(new SimpleGrantedAuthority("ROLE_USER")), Map.of("id", user.getGithubUserId(), "login", user.getLogin()), "id");
        } catch (RuntimeException failure) {
            throw new OAuth2AuthenticationException(new OAuth2Error("github_login_failed"), "GitHub sign-in could not be completed");
        }
    }
}
