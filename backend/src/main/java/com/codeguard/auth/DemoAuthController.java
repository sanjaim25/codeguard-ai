package com.codeguard.auth;

import jakarta.servlet.http.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.web.bind.annotation.*;

@RestController
@ConditionalOnProperty(name = "codeguard.demo", havingValue = "true")
public class DemoAuthController {
    private final DemoData demo;
    public DemoAuthController(DemoData demo) { this.demo = demo; }
    @PostMapping("/api/auth/demo")
    public ProfileController.Profile signIn(HttpServletRequest request, HttpServletResponse response) {
        var user = demo.signIn();
        var auth = UsernamePasswordAuthenticationToken.authenticated(user.getId().toString(), null, AuthorityUtils.createAuthorityList("ROLE_USER", "ROLE_DEMO"));
        new ChangeSessionIdAuthenticationStrategy().onAuthentication(auth, request, response);
        var context = SecurityContextHolder.createEmptyContext(); context.setAuthentication(auth);
        SecurityContextHolder.setContext(context);
        new HttpSessionSecurityContextRepository().saveContext(context, request, response);
        new HttpSessionCsrfTokenRepository().saveToken(null, request, response);
        return new ProfileController.Profile(user.getId(), user.getLogin(), user.getAvatarUrl());
    }
}
