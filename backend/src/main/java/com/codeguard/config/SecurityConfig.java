package com.codeguard.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import com.codeguard.auth.GithubOAuthUserService;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

@Configuration
public class SecurityConfig {
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, ObjectProvider<ClientRegistrationRepository> registrations,
                                           ObjectProvider<GithubOAuthUserService> userService, ObjectProvider<OAuth2AuthorizedClientRepository> clients,
                                           ObjectProvider<java.time.Clock> clocks) throws Exception {
        if (registrations.getIfAvailable() != null) {
            http.oauth2Login(oauth -> oauth.userInfoEndpoint(info -> info.userService(userService.getObject()))
                .authorizedClientRepository(clients.getObject()).defaultSuccessUrl("/app", true).failureUrl("/signin?error=github"));
        }
        return http
            .addFilterBefore(new com.codeguard.security.RequestRateLimitFilter(
                new com.codeguard.security.RequestRateLimiter(clocks.getIfAvailable(java.time.Clock::systemUTC), 10_000)),
                org.springframework.security.web.access.intercept.AuthorizationFilter.class)
            .csrf(csrf -> csrf.ignoringRequestMatchers(PathPatternRequestMatcher.withDefaults().matcher(org.springframework.http.HttpMethod.POST, "/api/webhooks/github")))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health", "/actuator/prometheus", "/api/system", "/api/csrf", "/api/auth/config", "/oauth2/**", "/login/oauth2/**").permitAll()
                .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/webhooks/github").permitAll()
                .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/auth/demo").permitAll()
                .anyRequest().authenticated())
            .exceptionHandling(errors -> errors.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
            .headers(headers -> headers
                .contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'none'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; connect-src 'self'; frame-ancestors 'none'; base-uri 'self'"))
                .frameOptions(frame -> frame.deny()))
            .logout(logout -> logout.logoutUrl("/api/logout").logoutSuccessHandler((req, res, auth) -> res.setStatus(204)))
            .build();
    }
}
