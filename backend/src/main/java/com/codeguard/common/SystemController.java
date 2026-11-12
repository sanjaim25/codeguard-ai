package com.codeguard.common;

import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SystemController {
    private final boolean demo;
    public SystemController(@Value("${codeguard.demo:false}") boolean demo) { this.demo = demo; }

    @GetMapping("/api/system")
    public Map<String, Object> system() { return Map.of("name", "CodeGuard AI", "demo", demo); }

    @GetMapping("/api/csrf")
    public Map<String, String> csrf(CsrfToken token) {
        return Map.of("headerName", token.getHeaderName(), "token", token.getToken());
    }
}
