package com.codeguard.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.session.data.redis.config.annotation.web.http.EnableRedisHttpSession;

/**
 * Enables Redis-backed HTTP sessions when SESSION_STORE_TYPE=redis.
 * By default (SESSION_STORE_TYPE=none), sessions remain in server memory.
 * See docs/DEPLOYMENT.md for horizontal scaling instructions.
 */
@Configuration
@ConditionalOnProperty(name = "spring.session.store-type", havingValue = "redis")
@EnableRedisHttpSession(maxInactiveIntervalInSeconds = 28800) // 8 hours, matches server.servlet.session.timeout
public class RedisSessionConfig {
    // Spring Session auto-configures the RedisConnectionFactory via spring.data.redis.*
}
